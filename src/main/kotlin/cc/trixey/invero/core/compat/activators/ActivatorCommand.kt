package cc.trixey.invero.core.compat.activators

import cc.trixey.invero.common.Invero
import cc.trixey.invero.common.Menu
import cc.trixey.invero.common.MenuActivator
import cc.trixey.invero.core.compat.DefActivator
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import kotlinx.serialization.json.*
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.*
import taboolib.common.platform.command.component.CommandBase
import taboolib.common.platform.command.component.CommandComponent

@DefActivator(["command", "commands", "cmd", "cmds"])
class ActivatorCommand(command: JsonElement) : MenuActivator<ActivatorCommand>() {

    constructor() : this(JsonPrimitive(0))

    var command: JsonElement = command
        private set
    private val registrations = CommandBindings<MenuCommandRegistration>()

    override fun setActivatorMenu(menu: Menu) = reload(menu, command)

    internal fun reload(menu: Menu, value: JsonElement) {
        val definitions = parseCommandBindings(value, Invero.API.getMenuManager().getJsonSerializer<Json>())
        FoliaRuntime.runGlobal {
            MenuCommandRegistration.validateOwnership(definitions, registrations.handles)
            val permissions = definitions.associate { it.name to MenuCommandRegistration.resolvePermission(it) }
            super.setActivatorMenu(menu)
            var refreshNeeded = false
            val previouslyRegistered = registrations.handles.isNotEmpty()
            activeActivators += this
            try {
                registrations.reconcile(definitions,
                    create = { definition ->
                        refreshNeeded = true
                        MenuCommandRegistration(definition, ::buildCommand).also { registration ->
                            try {
                                registration.update(definition, permissions.getValue(definition.name))
                            } catch (failure: Throwable) {
                                runCatching { registration.remove() }.onFailure { failure.addSuppressed(it) }
                                throw failure
                            }
                        }
                    },
                    update = { registration, definition ->
                        try {
                            refreshNeeded = registration.update(
                                definition, permissions.getValue(definition.name)
                            ) || refreshNeeded
                        } catch (failure: Throwable) {
                            refreshNeeded = true
                            throw failure
                        }
                    },
                    remove = {
                        refreshNeeded = true
                        it.remove()
                    })
                command = value
            } catch (failure: Throwable) {
                if (!previouslyRegistered) {
                    runCatching { registrations.clear { it.remove() } }.onFailure { failure.addSuppressed(it) }
                    activeActivators.remove(this)
                }
                throw failure
            } finally {
                if (refreshNeeded) MenuCommandRegistration.requestRefresh()
            }
        }
    }

    override fun unregister() {
        if (!MenuCommandRegistration.isEnabled) return
        FoliaRuntime.runGlobal { releaseCommands() }
        super.unregister()
    }

    private fun releaseCommands() {
        if (registrations.clear { it.remove() }) MenuCommandRegistration.requestRefresh()
        activeActivators.remove(this)
    }

    private fun buildCommand(definition: CommandStructure): CommandBase = CommandBase().apply {
        val arguments = definition.arguments.orEmpty()
        if (arguments.all { it.optional }) {
            execute<Player> { sender, context, _ ->
                activate(sender, commandVariables(arguments, context::getOrNull))
            }
        }
        var layer: CommandComponent = this
        arguments.forEach { argument ->
            layer = layer.dynamic(argument.id, argument.optional) {
                execute<Player> { sender, context, _ ->
                    activate(sender, commandVariables(arguments, context::getOrNull))
                }
                when (argument.type ?: CommandArgument.Type.ANY) {
                    CommandArgument.Type.ANY -> suggestion<Player>(!argument.restrict) { _, _ -> argument.suggest.orEmpty() }
                    CommandArgument.Type.DECIMAL -> restrictDouble()
                    CommandArgument.Type.INTEGER -> restrictInt()
                    CommandArgument.Type.BOOLEAN -> restrictBoolean()
                    CommandArgument.Type.PLAYER -> suggestPlayers(argument.suggest.orEmpty())
                    CommandArgument.Type.WORLD -> suggestWorlds(argument.suggest.orEmpty())
                }
            }
        }
    }

    override fun deserialize(element: JsonElement) = ActivatorCommand(element)
    override fun serialize(activator: ActivatorCommand) = activator.command

    companion object {
        private val activeActivators = mutableSetOf<ActivatorCommand>()

        @Awake(LifeCycle.DISABLE)
        fun disable() {
            activeActivators.toList().forEach { it.releaseCommands() }
        }
    }
}
