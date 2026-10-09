package cc.trixey.invero.core.api

import cc.trixey.invero.common.Invero
import cc.trixey.invero.common.Menu
import cc.trixey.invero.common.api.InveroMenuManager
import cc.trixey.invero.common.api.InveroSettings
import cc.trixey.invero.common.api.SerializeResult
import cc.trixey.invero.common.api.SerializeResult.State.*
import cc.trixey.invero.common.util.findInJar
import cc.trixey.invero.common.util.prettyPrint
import cc.trixey.invero.core.AgentPanel
import cc.trixey.invero.core.BaseMenu
import cc.trixey.invero.core.compat.activators.commandBindingValue
import cc.trixey.invero.core.compat.activators.parseCommandBindings
import cc.trixey.invero.core.action.*
import cc.trixey.invero.core.panel.PanelGenerator
import cc.trixey.invero.core.panel.PanelPaged
import cc.trixey.invero.core.panel.PanelScroll
import cc.trixey.invero.core.panel.PanelStandard
import cc.trixey.invero.core.serialize.BaseMenuSerializer
import cc.trixey.invero.core.serialize.hocon.PatchedLoader
import cc.trixey.invero.core.util.session
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.bukkit.util.runOnEntity
import cc.trixey.invero.ui.bukkit.util.runOnOwner
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.bukkit.command.CommandSender
import taboolib.common.LifeCycle
import taboolib.common.io.newFile
import taboolib.common.platform.Awake
import taboolib.common.platform.PlatformFactory
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getJarFile
import taboolib.common.platform.function.submitAsync
import taboolib.common5.FileWatcher
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
import taboolib.module.lang.sendLang
import taboolib.platform.util.onlinePlayers
import taboolib.platform.util.sendLang
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Invero
 * cc.trixey.invero.core.api.DefaultMenuManager
 *
 * @author Arasple
 * @since 2023/2/1 17:17
 */
class DefaultMenuManager : InveroMenuManager {

    @OptIn(ExperimentalSerializationApi::class)
    private val module = SerializersModule {

        polymorphic(Menu::class) {
            subclass(BaseMenu::class)
        }

        polymorphic(AgentPanel::class) {
            subclass(PanelStandard::class)
            subclass(PanelGenerator::class)
            subclass(PanelPaged::class)
            subclass(PanelScroll::class)
            subclass(PanelGenerator::class)
        }

        polymorphic(Action::class) {
            subclass(ActionKether::class)
            subclass(ConditionIf::class)
            subclass(ConditionAll::class)
            subclass(ConditionAny::class)
            subclass(ConditionNone::class)
            subclass(ConditionIfNot::class)
            subclass(ConditionCase::class)
            subclass(StructureActionKether::class)
            subclass(FunctionalActionCatcher::class)
            subclass(FunctionalActionCatchers::class)
            subclass(NetesedAction::class)
        }

    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        serializersModule = module
        explicitNulls = false
    }

    private val menus = ConcurrentHashMap<String, BaseMenu>()
    private val menuFiles = linkedMapOf<String, File>()
    private val watchedFiles = linkedSetOf<File>()
    private val versions = ReloadVersions()

    @Volatile
    private var closed = false

    override fun getMenu(id: String, ignoreCase: Boolean): BaseMenu? {
        return menus.entries.find { it.key.equals(id, ignoreCase) }?.value
    }

    override fun getMenus(): List<BaseMenu> {
        return menus.values.toList()
    }

    override fun deserialize(workspace: File): List<SerializeResult> = deserialize(workspace, menus)

    private fun deserialize(
        workspace: File,
        destination: MutableMap<String, BaseMenu>,
        expectedFiles: Set<File> = emptySet()
    ): List<SerializeResult> {
        val matcher = InveroSettings.fileFilter.toRegex()
        val results = mutableListOf<SerializeResult>()
        workspace.walk().filter { it.isFile && it.name.matches(matcher) }.forEach { file ->
            val configuration = runCatching { PatchedLoader.loadFromFile(file) }.onFailure {
                results += SerializeResult(file = file, state = FAILURE_FILE, throwable = it)
            }.getOrNull() ?: return@forEach
            if (menuDeclarations.none { it in configuration.getKeys(false) }) {
                if (file.normalized() in expectedFiles) results += SerializeResult(file = file, state = FAILURE_MENU,
                    throwable = IllegalArgumentException("Menu configuration has no menu/title section: $file"))
                return@forEach
            }
            val menu = runCatching { deserializeToMenu(configuration, configuration.name) }.onFailure {
                results += SerializeResult(file = file, state = FAILURE_MENU, throwable = it)
            }.getOrNull() ?: return@forEach
            val id = menu.id!!
            if (destination.keys.any { it.equals(id, ignoreCase = true) }) {
                results += SerializeResult(file = file, state = FAILURE_DUPLICATED)
            } else {
                destination[id] = menu
                results += SerializeResult(menu, file, SUCCESS)
            }
        }
        return results
    }

    override fun reload(receiver: CommandSender) {
        if (closed) return
        val start = System.currentTimeMillis()
        val ticket = versions.beginReload()
        FoliaRuntime.runGlobal {
            val expectedFiles = menuFiles.values.toSet()
            submitAsync {
                val staged = linkedMapOf<String, BaseMenu>()
                val workspaces = runCatching { initWorkspaces() }.onFailure { it.prettyPrint() }
                    .getOrNull() ?: return@submitAsync
                val results = runCatching { workspaces.flatMap { deserialize(it, staged, expectedFiles) } }
                    .onFailure { it.prettyPrint() }.getOrNull() ?: return@submitAsync
                if (closed) return@submitAsync
                FoliaRuntime.runGlobal apply@{
                    if (closed || !versions.isCurrent(ticket)) return@apply
                    if (workspaces.isEmpty()) notify(receiver, "menu-loader-workspace-empty")
                    else notify(receiver, "menu-loader-workspace-inited", workspaces.size)
                    val failed = results.filter { it.state != SUCCESS }.mapTo(hashSetOf()) { it.file.normalized() }
                    val previousFiles = menuFiles.toMap()
                    results.forEach { result ->
                        val file = result.file.normalized()
                        when (result.state) {
                            FAILURE_FILE -> notify(receiver, "menu-loader-file-errored", file.name)
                            FAILURE_MENU -> notify(receiver, "menu-loader-menu-errored", file.name)
                            FAILURE_DUPLICATED -> notify(receiver, "menu-loader-menu-duplicate", file.name)
                            SUCCESS -> if (file.exists() && versions.isCurrent(ticket, file.path)) {
                                if (!replaceMenu(result.menu as BaseMenu, file)) {
                                    failed += file
                                    notify(receiver, "menu-loader-menu-errored", file.name)
                                }
                            }
                        }
                        result.print()
                    }
                    if (workspaces.isNotEmpty()) previousFiles.forEach { (id, file) ->
                        if (id !in staged && file !in failed && versions.isCurrent(ticket, file.path)) removeMenu(id)
                    }
                    notify(receiver, "menu-loader-menu-finished", menus.size,
                        (System.currentTimeMillis() - start) / 1000.0)
                }
            }
        }
    }

    private fun replaceMenu(loaded: BaseMenu, file: File): Boolean {
        val id = loaded.id!!
        val previousId = if (menus.containsKey(id)) id else menuFiles.entries.find { it.value == file }?.key
        val previous = previousId?.let { menus[it] }
        val previousFile = previousId?.let { menuFiles[it] }
        if (previousFile != null && previousFile != file && previousFile.exists() && included(previousFile)) {
            console().sendLang("menu-loader-menu-duplicate", id)
            return false
        }
        if (runCatching { loaded.register(previous) }.onFailure {
            loaded.unregister()
            it.prettyPrint()
        }.isFailure) return false
        menus[id] = loaded
        previous?.unregister()
        if (previousId != null && previousId != id) {
            menus.remove(previousId)
            menuFiles.remove(previousId)
        }
        menuFiles[id] = file
        if (InveroSettings.fileListener) registerListener(file)
        refreshViewers(previousId ?: id, loaded)
        return true
    }

    private fun removeMenu(id: String) {
        menus[id]?.unregister()
        menus.remove(id)
        menuFiles.remove(id)
        refreshViewers(id, null)
    }

    private fun refreshViewers(id: String, loaded: BaseMenu?) {
        onlinePlayers.forEach { player ->
            player.runOnEntity {
                val current = if (loaded == null) !menus.containsKey(id) else menus[loaded.id] === loaded
                if (closed || !current) return@runOnEntity
                val session = player.session ?: return@runOnEntity
                if (session.menu.id != id) return@runOnEntity
                val variables = session.getVariables()
                session.menu.close(player, closeWindow = loaded == null, closeInventory = loaded == null)
                loaded?.open(player, variables)
            }
        }
    }

    private fun notify(receiver: CommandSender, key: String, vararg args: Any) {
        receiver.runOnOwner { receiver.sendLang(key, *args) }
    }

    private fun registerListener(file: File) {
        if (!watchedFiles.add(file)) return
        runCatching { FileWatcher.INSTANCE.addSimpleListener(file) { reloadFile(file) } }.onFailure {
            watchedFiles.remove(file)
            it.prettyPrint()
        }
    }

    private fun reloadFile(file: File) {
        if (closed || !InveroSettings.fileListener) return
        val ticket = versions.beginChange(file.path)
        submitAsync(delay = 2L) {
            if (closed || !versions.isCurrent(ticket, file.path)) return@submitAsync
            val loaded = runCatching {
                if (!file.exists() || !included(file)) null else {
                    val configuration = PatchedLoader.loadFromFile(file)
                    require(menuDeclarations.any { it in configuration.getKeys(false) }) {
                        "Menu configuration has no menu/title section: $file"
                    }
                    deserializeToMenu(configuration, file.nameWithoutExtension)
                }
            }.onFailure {
                it.prettyPrint()
                console().sendLang("menu-loader-auto-reload-errored", file.nameWithoutExtension)
            }.getOrElse { return@submitAsync }
            if (closed) return@submitAsync
            FoliaRuntime.runGlobal apply@{
                if (closed || !versions.isCurrent(ticket, file.path)) return@apply
                val previousId = menuFiles.entries.find { it.value == file }?.key
                if (loaded == null) previousId?.let { removeMenu(it) } else {
                    if (menus.containsKey(loaded.id!!) && menuFiles[loaded.id] != file) {
                        console().sendLang("menu-loader-menu-duplicate", loaded.id!!)
                        return@apply
                    }
                    if (!replaceMenu(loaded, file)) {
                        console().sendLang("menu-loader-auto-reload-errored", loaded.id!!)
                        return@apply
                    }
                    console().sendLang("menu-loader-auto-reload-successed", loaded.id!!)
                }
            }
        }
    }

    private fun dispose() {
        closed = true
        versions.invalidate()
        watchedFiles.forEach { FileWatcher.INSTANCE.removeListener(it) }
        watchedFiles.clear()
        menus.values.forEach { it.unregister() }
        menus.clear()
        menuFiles.clear()
    }

    private fun included(file: File): Boolean = file.name.matches(InveroSettings.fileFilter.toRegex()) &&
        InveroSettings.workspaces.any { file.toPath().startsWith(File(it).normalized().toPath()) }

    private fun File.normalized(): File = absoluteFile.normalize()

    override fun deserializeToMenu(configuration: Configuration, name: String?): BaseMenu {
        configuration.changeType(Type.JSON)
        return json
            .decodeFromString(BaseMenuSerializer, configuration.saveToString())
            .also {
                if (name != null && it.id == null) it.id = name
                commandBindingValue(it.bindings)?.let { value -> parseCommandBindings(value, json) }
            }
    }

    override fun serializeToJson(menu: Menu): String {
        return json.encodeToString(value = menu)
    }

    override fun <T> getJsonSerializer(): T {
        @Suppress("UNCHECKED_CAST")
        return json as T
    }

    private val menuDeclarations = mutableSetOf("menu", "title")

    companion object {

        @Awake(LifeCycle.ACTIVE)
        fun init() {
            PlatformFactory.registerAPI<InveroMenuManager>(DefaultMenuManager())

            submitAsync(delay = 15L) {
                Invero.API.getMenuManager().reload()
            }
        }

        @Awake(LifeCycle.DISABLE)
        fun disable() {
            (Invero.API.getMenuManager() as? DefaultMenuManager)?.dispose()
        }

        fun initWorkspaces(): List<File> {
            val list = ArrayList<File>()

            for (path in InveroSettings.workspaces) {
                val file = File(path)
                if (!file.exists()) { 
                    releaseWorkspace(file)
                }
                if (file.isDirectory) list.add(file)
            }

            return list
        }

        /**
         * 复制默认工作空间内文件到默认工作空间
         */
        fun releaseWorkspace(folder: File) {
            findInJar(getJarFile()) {
                !it.isDirectory && it.name.startsWith("default/")
            }.forEach {
                newFile(File(folder, it.first.name.substringAfter('/'))).writeBytes(it.second.readBytes())
            }
        }

    }

}