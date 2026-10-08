package cc.trixey.invero.core.util

import cc.trixey.invero.common.util.alert
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.bukkit.util.runOnEntity
import org.bukkit.entity.Player
import taboolib.common.platform.function.adaptPlayer
import taboolib.common.platform.function.console
import taboolib.library.reflex.Reflex.Companion.setProperty
import taboolib.module.kether.KetherFunction
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions
import taboolib.module.kether.runKether
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Invero
 * cc.trixey.invero.core.util.KetherHandler
 *
 * @author Arasple
 * @since 2023/1/16 13:14
 */
object KetherHandler {

    private val namespace = listOf("invero")

    fun invoke(source: String, player: Player?, vars: Map<String, Any?>): CompletableFuture<Any?> {
        val result = CompletableFuture<Any?>()
        val evaluate = {
            val future = alert {
                runKether {
                    KetherShell.eval(
                        source,
                        ScriptOptions.new {
                            namespace(namespace)
                            sender(if (player != null) adaptPlayer(player) else console())
                            vars(vars)
                            if (player != null && FoliaRuntime.isFolia) {
                                context {
                                    executor.setProperty(
                                        "actual",
                                        Executor { command -> player.runOnEntity { command.run() } },
                                        remap = false
                                    )
                                }
                            }
                        }
                    )
                }
            } ?: CompletableFuture.completedFuture(null)
            future.whenComplete { value, throwable ->
                if (throwable != null) result.completeExceptionally(throwable)
                else result.complete(value)
            }
            Unit
        }
        if (player != null) player.runOnEntity(evaluate) else FoliaRuntime.runGlobal(evaluate)
        return result
    }

    fun parseInline(source: String, player: Player?, vars: Map<String, Any?>) = alert {
        KetherFunction.parse(
            source,
            ScriptOptions.new {
                namespace(namespace)
                sender(if (player != null) adaptPlayer(player) else console())
                vars(vars)
            }
        )
    } ?: "<ERROR: $source>"

}