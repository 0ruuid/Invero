package cc.trixey.invero.common

import org.bukkit.Bukkit
import cc.trixey.invero.ui.bukkit.PlayerViewer
import cc.trixey.invero.ui.bukkit.util.submitOnEntity
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.platform.Folia
import taboolib.platform.util.bukkitPlugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Invero
 * cc.trixey.invero.common.TaskGroup
 *
 * @author Arasple
 * @since 2023/1/16 12:14
 */
class TaskGroup(
    private val viewer: PlayerViewer,
    private val platformTasks: CopyOnWriteArraySet<PlatformExecutor.PlatformTask> = CopyOnWriteArraySet()
) {

    fun unregisterAll() {
        platformTasks.removeIf {
            it.cancel()
            true
        }
    }

    fun launch(
        now: Boolean = false,
        async: Boolean = false,
        delay: Long = 0,
        period: Long = 0,
        executor: (task: PlatformExecutor.PlatformTask) -> Unit,
    ) {
        val task = if (async) {
            submit(now, true, delay, period, executor)
        } else {
            val player = viewer.get<org.bukkit.entity.Player>() ?: return
            player.submitOnEntity(delay, period, executor)
        }
        this += task
    }

    operator fun plusAssign(task: PlatformExecutor.PlatformTask) {
        platformTasks += task
    }

    override fun toString(): String {
        return "MenuTasks(platformTasks: ${platformTasks.size})"
    }

    companion object {

        private val taskMgrs = ConcurrentHashMap<String, TaskGroup>()

        fun get(viewer: PlayerViewer): TaskGroup {
            return taskMgrs.computeIfAbsent(viewer.name) { TaskGroup(viewer) }
        }

        @Awake(LifeCycle.DISABLE)
        fun unregister() {
            taskMgrs.values.forEach { it.unregisterAll() }

            if (!Folia.isFolia) {
                Bukkit.getScheduler().apply {
                    pendingTasks
                        .filter { it.owner == bukkitPlugin && !it.isCancelled }
                        .forEach { it.cancel() }
                }
            }
        }

    }

}