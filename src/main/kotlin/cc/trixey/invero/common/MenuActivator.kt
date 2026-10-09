package cc.trixey.invero.common

import org.bukkit.entity.Player

/**
 * Invero
 * cc.trixey.invero.common.MenuActivator
 *
 * @author Arasple
 * @since 2023/2/25 13:54
 */
abstract class MenuActivator<T> : CustomSerializable<T> {

    @Volatile
    private var menuId: String? = null

    open fun setActivatorMenu(menu: Menu) {
        menuId = menu.id
    }

    open fun register() {
    }

    open fun unregister() {
    }

    open fun call(player: Player, vararg params: Any): Boolean {
        return false
    }

    protected fun activate(player: Player, variables: Map<String, Any> = emptyMap()) {
        menuId?.let {
            Invero.API.getMenuManager().getMenu(it)?.open(player, variables)
        }
    }

}