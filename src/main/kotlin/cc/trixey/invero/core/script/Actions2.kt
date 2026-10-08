package cc.trixey.invero.core.script

import cc.trixey.invero.core.script.loader.InveroKetherParser
import cc.trixey.invero.core.script.override.ActionCommand
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.bukkit.util.runOnEntity
import taboolib.common.platform.function.console
import taboolib.module.kether.combinationParser
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.script.kether.Actions2
 *
 * @author Arasple
 * @since 2023/2/26 18:01
 */
@InveroKetherParser(["chance"])
internal fun chance() = combinationParser {
    it.group(double()).apply(it) { random ->
        val chance = if (random > 1) random else random * 100.0
        now {
            return@now chance > (0..100).random()
        }
    }
}

@InveroKetherParser(["playerPerform"])
internal fun command() = combinationParser {
    it.group(action()).apply(it) { s ->
        future { ActionCommand(s, ActionCommand.Type.PLAYER).run(this) }
    }
}

@InveroKetherParser(["console"])
internal fun actionConsole() = combinationParser {
    it.group(text()).apply(it) { s ->
        future {
            val future = CompletableFuture<Any?>()
            val player = player()
            val command = parse(s)
            FoliaRuntime.runGlobal {
                runCatching { console().performCommand(command) }
                    .fold(
                        { player.runOnEntity { future.complete(it) } },
                        { player.runOnEntity { future.completeExceptionally(it) } }
                    )
            }
            future
        }
    }
}