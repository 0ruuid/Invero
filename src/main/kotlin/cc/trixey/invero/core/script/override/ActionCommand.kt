package cc.trixey.invero.core.script.override

import cc.trixey.invero.core.script.loader.InveroKetherParser
import cc.trixey.invero.core.script.parse
import cc.trixey.invero.core.script.player
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.bukkit.util.runOnEntity
import taboolib.common.platform.function.console
import taboolib.library.kether.ParsedAction
import taboolib.module.kether.*
import java.util.concurrent.CompletableFuture

class ActionCommand(
    private val command: ParsedAction<*>,
    private val type: Type
) : ScriptAction<Void>() {

    enum class Type {
        PLAYER, OPERATOR, CONSOLE
    }

    override fun run(frame: ScriptFrame): CompletableFuture<Void> {
        return frame.run(command).thenCompose { raw ->
            val future = CompletableFuture<Void>()
            val player = frame.player()
            player.runOnEntity {
                val content = frame.parse(raw.toString().trimIndent(), true)
                when (type) {
                    Type.PLAYER -> runCatching {
                        player.performCommand(content.replace("@sender", player.name))
                    }.fold({ future.complete(null) }, future::completeExceptionally)

                    Type.OPERATOR -> {
                        val wasOp = player.isOp
                        runCatching {
                            player.isOp = true
                            try {
                                player.performCommand(content.replace("@sender", player.name))
                            } finally {
                                player.isOp = wasOp
                            }
                        }.fold({ future.complete(null) }, future::completeExceptionally)
                    }

                    Type.CONSOLE -> FoliaRuntime.runGlobal {
                        runCatching {
                            console().performCommand(
                                content.replace("@sender", "console").replace("@p", player.name)
                            )
                        }.fold(
                            { future.complete(null) },
                            future::completeExceptionally
                        )
                    }
                }
            }
            future
        }
    }

    object Parser {

        @InveroKetherParser(["command"])
        fun parser() = scriptParser {
            val command = it.nextParsedAction()
            it.mark()
            val type = try {
                it.expects("by", "with", "as")
                when (val value = it.nextToken().lowercase()) {
                    "player" -> Type.PLAYER
                    "op", "operator" -> Type.OPERATOR
                    "console", "server" -> Type.CONSOLE
                    else -> throw KetherError.NOT_COMMAND_SENDER.create(value)
                }
            } catch (_: Exception) {
                it.reset()
                Type.PLAYER
            }
            ActionCommand(command, type)
        }
    }
}
