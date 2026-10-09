package cc.trixey.invero.ui.bukkit.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.function.isPrimaryThread
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.library.reflex.Reflex.Companion.invokeMethod
import taboolib.platform.Folia
import taboolib.platform.util.runTask
import taboolib.platform.util.submit as submitAtOwner
import java.util.concurrent.CompletableFuture

/** Paper/Folia 调度门面。 */
object FoliaRuntime {

    val isFolia: Boolean
        get() = Folia.isFolia

    /** 在玩家所属线程执行任务。 */
    fun runPlayer(player: Player, action: () -> Unit) {
        if (isFolia && !isOwnedByCurrentRegion(player)) {
            player.runTask(Runnable(action))
        } else if (isFolia || isPrimaryThread) {
            action()
        } else {
            submit { action() }
        }
    }

    /** 将任务提交到玩家所属线程。 */
    fun schedulePlayer(player: Player, action: () -> Unit) {
        if (isFolia) {
            player.runTask(Runnable(action))
        } else {
            submit { action() }
        }
    }

    /** 提交玩家任务，延迟和周期单位为 tick。 */
    fun submitPlayer(
        player: Player,
        delay: Long = 0,
        period: Long = 0,
        action: PlatformExecutor.PlatformTask.() -> Unit,
    ): PlatformExecutor.PlatformTask {
        return if (isFolia) {
            player.submitAtOwner(delay = delay, period = period, executor = action)
        } else {
            submit(delay = delay, period = period, executor = action)
        }
    }

    private val inGlobalAction = ThreadLocal.withInitial { false }

    /** 在全局线程执行任务。 */
    fun runGlobal(action: () -> Unit) {
        if (isGlobalOwner()) {
            action()
        } else {
            submit { runGlobalAction(action) }
        }
    }

    private fun isGlobalOwner(): Boolean {
        if (!isFolia) return isPrimaryThread
        if (inGlobalAction.get()) return true

        return runCatching {
            Bukkit::class.java.invokeMethod<Boolean>("isGlobalTickThread", isStatic = true, remap = false)
        }.getOrNull() == true
    }

    private fun runGlobalAction(action: () -> Unit) {
        inGlobalAction.set(true)
        try {
            action()
        } finally {
            inGlobalAction.remove()
        }
    }

    /** 提交全局任务，延迟和周期单位为 tick。 */
    fun submitGlobal(
        delay: Long = 0,
        period: Long = 0,
        action: PlatformExecutor.PlatformTask.() -> Unit,
    ): PlatformExecutor.PlatformTask {
        return submit(delay = delay, period = period, executor = action)
    }

    fun <T> supplyPlayer(player: Player, action: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val timeout = submitGlobal(delay = 100L) {
            future.completeExceptionally(IllegalStateException("Player scheduler did not accept task: ${player.name}"))
        }
        future.whenComplete { _, _ -> timeout.cancel() }
        runPlayer(player) {
            runCatching(action).fold(future::complete, future::completeExceptionally)
        }
        return future
    }

    fun <T> supplyGlobal(action: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        runGlobal { runCatching(action).fold(future::complete, future::completeExceptionally) }
        return future
    }

    fun <T> supplyLocation(location: Location, action: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val timeout = submitGlobal(delay = 100L) {
            future.completeExceptionally(IllegalStateException("Region scheduler did not accept task: $location"))
        }
        future.whenComplete { _, _ -> timeout.cancel() }
        location.runTask(Runnable {
            runCatching(action).fold(future::complete, future::completeExceptionally)
        })
        return future
    }

    private fun isOwnedByCurrentRegion(player: Player): Boolean {
        return runCatching {
            Bukkit::class.java.invokeMethod<Boolean>("isOwnedByCurrentRegion", player, isStatic = true, remap = false)
        }.getOrNull() == true
    }
}

fun Player.runOnEntity(action: () -> Unit) = FoliaRuntime.runPlayer(this, action)

fun Player.scheduleOnEntity(action: () -> Unit) = FoliaRuntime.schedulePlayer(this, action)

fun CommandSender.runOnOwner(action: () -> Unit) {
    if (this is Player) runOnEntity(action) else FoliaRuntime.runGlobal(action)
}

fun Player.submitOnEntity(
    delay: Long = 0,
    period: Long = 0,
    action: PlatformExecutor.PlatformTask.() -> Unit,
) = FoliaRuntime.submitPlayer(this, delay, period, action)

fun <T> Player.supplyOnEntity(action: () -> T) = FoliaRuntime.supplyPlayer(this, action)
