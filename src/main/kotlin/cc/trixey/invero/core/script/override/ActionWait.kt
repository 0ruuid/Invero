package cc.trixey.invero.core.script.override

import cc.trixey.invero.core.script.loader.InveroKetherParser
import cc.trixey.invero.core.script.player
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.bukkit.util.submitOnEntity
import taboolib.library.kether.ArgTypes
import taboolib.module.kether.ScriptService
import taboolib.module.kether.actionFuture
import taboolib.module.kether.script
import taboolib.module.kether.scriptParser

@InveroKetherParser(["wait", "delay", "sleep"])
fun actionWait() = scriptParser {
    val ticks = it.next(ArgTypes.DURATION).toMillis() / 50L
    actionFuture { future ->
        val frame = this
        val player = runCatching { frame.player() }.getOrNull()
        val complete = {
            if (frame.script().sender?.isOnline() == false) {
                ScriptService.terminateQuest(frame.script())
            } else {
                future.complete(null)
            }
        }
        val task = if (player != null) {
            player.submitOnEntity(delay = ticks) { complete() }
        } else {
            FoliaRuntime.submitGlobal(delay = ticks) { complete() }
        }
        frame.addClosable(AutoCloseable { task.cancel() })
    }
}
