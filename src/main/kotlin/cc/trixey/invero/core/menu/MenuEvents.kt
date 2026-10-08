@file:OptIn(ExperimentalSerializationApi::class)

package cc.trixey.invero.core.menu

import cc.trixey.invero.core.Context
import cc.trixey.invero.core.Session
import cc.trixey.invero.core.action.Action
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.menu.MenuEvents
 *
 * @author Arasple
 * @since 2023/1/25 11:36
 */
@Serializable
class MenuEvents(
    @JsonNames("pre_open", "open")
    private val preOpen: Action? = null,
    @JsonNames("post_open", "opened")
    private val postOpen: Action? = null,
    @JsonNames("close")
    private val close: Action? = null,
) {

    /**
     * Instant
     */
    fun preOpen(session: Session): CompletableFuture<Boolean> {
        return preOpen?.run(Context(session.viewer, session)) ?: CompletableFuture.completedFuture(true)
    }

    /**
     * Async
     */
    fun postOpen(session: Session) {
        postOpen?.run(Context(session.viewer, session))
    }

    /**
     * Async
     */
    fun close(session: Session) {
        if (session.viewer.isAvailable()) {
            close?.run(Context(session.viewer, session))
        }
    }

}