package cc.trixey.invero.core.menu

import cc.trixey.invero.core.Session
import cc.trixey.invero.core.serialize.ListNodeSerializer
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Invero
 * cc.trixey.invero.core.menu.MenuTask
 *
 * @author Arasple
 * @since 2023/2/8 12:33
 */
@Serializable
class MenuTask(
    val delay: Long?,
    val period: Long?,
    val async: Boolean?,
    @SerialName("run")
    @Serializable(with = ListNodeSerializer::class)
    val runnables: List<NodeRunnable>
) {

    fun submit(session: Session) {
        val runAsync = (async ?: true) && !FoliaRuntime.isFolia
        session.taskGroup.launch(async = runAsync, delay = delay ?: 0L, period = period ?: 0L) {
            run(session)
        }
    }

    fun run(session: Session, params: Map<String, Any> = emptyMap()) {
        runnables.forEach { it.invoke(session, params) }
    }

}