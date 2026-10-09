package cc.trixey.invero.core.compat.activators

import cc.trixey.invero.common.util.prettyPrint
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import org.bukkit.Bukkit
import org.bukkit.command.PluginCommand
import org.bukkit.permissions.Permission
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.CommandContext
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.component.CommandBase
import taboolib.common.platform.function.adaptCommandSender
import taboolib.common.platform.function.commandService
import taboolib.common.platform.function.console
import taboolib.common.platform.service.PlatformCommand
import taboolib.library.reflex.Reflex.Companion.invokeMethod
import taboolib.library.reflex.ReflexClass
import taboolib.module.lang.sendLang
import taboolib.platform.BukkitCommand
import taboolib.common.platform.command.CommandStructure as Descriptor

/** 菜单命令的 Bukkit 注册适配。 */
internal class MenuCommandRegistration(
    definition: CommandStructure,
    private val build: (CommandStructure) -> CommandBase
) {
    val command: PluginCommand = platform.constructor.newInstance(definition.name, platform.plugin)

    private data class Runtime(
        val definition: CommandStructure,
        val descriptor: Descriptor,
        val active: Boolean
    )

    @Volatile
    private var runtime = Runtime(definition, descriptor(definition), false)

    init {
        command.setExecutor { sender, _, label, args ->
            val current = runtime
            if (!current.active || args.size < current.definition.minimumArguments()) false else {
                val base = build(current.definition)
                base.execute(CommandContext(adaptCommandSender(sender), current.descriptor, label, base, false, args))
            }
        }
        initializeLegacyTimings()
        command.setTabCompleter { sender, _, label, args ->
            val current = runtime
            if (!current.active) emptyList() else {
                val base = build(current.definition)
                base.suggest(CommandContext(adaptCommandSender(sender), current.descriptor, label, base, false, args))
                    ?: emptyList()
            }
        }
    }

    /** 初始化旧版 Bukkit 的命令计时器。 */
    private fun initializeLegacyTimings() {
        runCatching {
            val field = ReflexClass.of(command.javaClass).getFieldSilently("timings") ?: return@runCatching
            if (field.get(command) != null) return@runCatching
            val manager = Class.forName("co.aikar.timings.TimingsManager")
            val timing = manager.invokeMethod<Any>(
                "getCommandTiming", platform.plugin.name, command, isStatic = true
            )
            field.set(command, timing)
        }.onFailure {
            if (it !is ClassNotFoundException && it !is NoClassDefFoundError) it.prettyPrint()
        }
    }

    fun update(definition: CommandStructure, permission: String = resolvePermission(definition)): Boolean {
        val descriptor = descriptor(definition)
        val refreshNeeded = !command.isRegistered || command.permission != permission
        val permissions = Bukkit.getPluginManager()
        if (permissions.getPermission(permission) == null) {
            permissions.addPermission(Permission(permission,
                org.bukkit.permissions.PermissionDefault.valueOf(descriptor.permissionDefault.name)))
        }
        command.setDescription(definition.description.orEmpty().ifEmpty { definition.name })
        command.setUsage(definition.usage.orEmpty().ifEmpty {
            "/${definition.name}" + definition.arguments.orEmpty().joinToString("") {
                if (it.optional) " [${it.id}]" else " <${it.id}>"
            }
        })
        command.setAliases(definition.aliases.orEmpty())
        command.setPermission(permission)
        command.setPermissionMessage(definition.permissionMessage.orEmpty().ifEmpty { PlatformCommand.defaultPermissionMessage })
        runtime = Runtime(definition, descriptor, true)
        if (!command.isRegistered && !platform.commandMap.register(platform.plugin.name.lowercase(), command)) {
            console().sendLang(
                "menu-command-label-conflict", definition.name,
                "${platform.plugin.name.lowercase()}:${definition.name}"
            )
        }
        return refreshNeeded
    }

    fun remove() {
        runtime = runtime.copy(active = false)
        val labels = platform.knownCommands.entries.filter { it.value === command }.map { it.key }
        try {
            labels.forEach { if (platform.knownCommands[it] === command) platform.knownCommands.remove(it) }
        } finally {
            command.unregister(platform.commandMap)
        }
    }

    companion object {

        private val platform: BukkitCommand
            get() = commandService as BukkitCommand

        val isEnabled: Boolean
            get() = platform.plugin.isEnabled

        private val refresh = CommandRefresh(
            schedule = { task -> FoliaRuntime.submitGlobal(delay = 1L) { task() } },
            refresh = {
                runCatching { Bukkit.getServer().invokeMethod<Void>("syncCommands") }.onFailure {
                    if (it !is NoSuchMethodException && it !is NoSuchMethodError) it.prettyPrint()
                }
            }
        )

        fun validateOwnership(
            definitions: List<CommandStructure>,
            registrations: List<MenuCommandRegistration>
        ) {
            val owned = registrations.map { it.command }
            val prefix = platform.plugin.name.lowercase()
            definitions.flatMap { listOf(it.name) + it.aliases.orEmpty() }.forEach { label ->
                val existing = platform.knownCommands["$prefix:$label"]
                require(existing == null || owned.any { it === existing }) {
                    "Command label already owned: $label"
                }
            }
        }

        fun resolvePermission(definition: CommandStructure): String {
            val descriptor = descriptor(definition)
            return descriptor.permission.ifEmpty { BukkitCommand.permissionProvider(descriptor) }
        }

        fun requestRefresh() {
            if (isEnabled) refresh.request()
        }

        @Awake(LifeCycle.DISABLE)
        fun disable() = refresh.stop()

        private fun descriptor(definition: CommandStructure) = Descriptor(
            definition.name, definition.aliases.orEmpty(), definition.description.orEmpty(), definition.usage.orEmpty(),
            definition.permission.orEmpty(), definition.permissionMessage.orEmpty(),
            if (definition.permission.isNullOrEmpty()) PermissionDefault.TRUE else PermissionDefault.OP,
            emptyMap(), false
        )
    }
}
