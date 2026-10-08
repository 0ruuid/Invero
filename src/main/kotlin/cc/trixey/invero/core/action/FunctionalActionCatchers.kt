package cc.trixey.invero.core.action

import cc.trixey.invero.common.Invero
import cc.trixey.invero.core.Context
import cc.trixey.invero.ui.bukkit.util.submitOnEntity
import kotlinx.serialization.Serializable
import org.bukkit.entity.Player
import taboolib.library.reflex.Reflex.Companion.setProperty
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.action.FunctionalActionCatcher
 *
 * @author Arasple
 * @since 2023/1/31 21:10
 */
@Serializable
class FunctionalActionCatchers(
    val catchers: List<InputCatcher>,
    val reopen: Boolean = true
) : Action() {

    init {
        // inherit
        val head = catchers.first()
        val defType = head.type
        for (i in 1 until catchers.size) {
            val catcher = catchers[i]
            if (catcher.type != defType && catcher.content == null && catcher.signLine == null) catcher.setProperty(
                "type",
                defType
            )
            if (catcher.cancel == null) catcher.setProperty("cancel", head.cancel)
            if (catcher.beforeInput == null) catcher.setProperty("beforeInput", head.beforeInput)
            if (catcher.onRepeat == null) catcher.setProperty("onRepeat", head.onRepeat)
            if (catcher.onCancel == null) catcher.setProperty("onCancel", head.onCancel)
        }
    }

    override fun run(context: Context): CompletableFuture<Boolean> {
        val session = context.session ?: error("FunctionalActionCatcher can only be used when there is a valid session")
        val cache = context.contextVariables
        val menu = session.menu.id
        val player = context.viewer.get<Player>() ?: return CompletableFuture.completedFuture(false)
        // close window
        session.menu.close(player)
        // processor
        fun process(iterator: Iterator<InputCatcher>) {
            if (!iterator.hasNext()) {
                if (reopen && menu != null) Invero.API.getMenuManager().getMenu(menu)?.open(player, context.variables)
                return
            }
            iterator.next().run(player, context) { process(iterator) }
        }
        player.submitOnEntity(delay = 2L) {
            process(catchers.iterator())
        }
        return CompletableFuture.completedFuture(false)
    }

}