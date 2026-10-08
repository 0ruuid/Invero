@file:OptIn(ExperimentalSerializationApi::class)

package cc.trixey.invero.core.action

import cc.trixey.invero.common.Invero
import cc.trixey.invero.core.Context
import cc.trixey.invero.core.serialize.ListStringSerializer
import cc.trixey.invero.ui.bukkit.util.submitOnEntity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.action.FunctionalActionCatcher
 *
 * @author Arasple
 * @since 2023/1/31 21:10
 */
@Serializable
class FunctionalActionCatcher(
    val catch: String,
    val content: JsonElement?,
    @JsonNames("signIndex", "index", "line")
    val signLine: Int?,
    @Serializable(ListStringSerializer::class)
    val cancel: List<String>?,
    val reopen: Boolean = true,
    val beforeInput: Action?,
    val afterInput: Action?,
    val onRepeat: Action?,
    val onCancel: Action?,
) : Action() {

    @Transient
    private val inputCatcher =
        InputCatcher(catch, content, signLine, cancel, beforeInput, afterInput, onRepeat, onCancel)

    override fun run(context: Context): CompletableFuture<Boolean> {
        val session = context.session ?: error("FunctionalActionCatcher can only be used when there is a valid session")
        val menu = session.menu.id
        val player = context.viewer.get<Player>() ?: return CompletableFuture.completedFuture(false)
        // close window
        session.menu.close(player)
        // input
        player.submitOnEntity(delay = 2L) {
            inputCatcher.run(player, context) {
                val pass = context.variables.filterNot { it.key.startsWith("@") }
                if (reopen && menu != null)
                    Invero
                        .API
                        .getMenuManager()
                        .getMenu(menu)
                        ?.open(player, pass)
            }
        }
        return CompletableFuture.completedFuture(false)
    }

}