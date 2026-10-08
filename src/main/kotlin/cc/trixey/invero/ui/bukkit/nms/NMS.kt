package cc.trixey.invero.ui.bukkit.nms

import cc.trixey.invero.ui.common.ContainerType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Invero
 * cc.trixey.invero.ui.bukkit.nms.NMS
 *
 * @author Arasple
 * @since 2022/10/20
 */
interface NMS {

    /**
     * This is sent to the client when it should open an inventory,
     * such as a chest, workbench, or furnace.
     * This message is not sent anywhere for clients opening their own inventory. For horses, use Open Horse Window.
     *
     * A unique id number for the window to be displayed. Notchian server implementation is a counter, starting at 1.
     *
     * @param type The window type to use for display. See WindowProperty for the different values.
     * @param rawTitle The title of the window
     */
    fun sendWindowOpen(player: Player, containerId: Int, type: ContainerType, rawTitle: String)

    /**
     * This packet is sent by the client when closing a window.
     * Notchian clients send a Close Window packet with Window ID 0 to close their inventory even though
     * there is never an Open Window packet for the inventory.
     *
     * This is the ID of the window that was closed. 0 for player inventory.
     */
    fun sendWindowClose(player: Player, containerId: Int)

    /**
     * Sent by the server when items in multiple slots (in a window) are added/removed.
     * This includes the main inventory, equipped armour and crafting slots.
     *
     * The ID of window which items are being sent for. 0 for player inventory.
     *
     * @param itemStacks Array of Slot
     */
    fun sendWindowItems(player: Player, containerId: Int, itemStacks: List<ItemStack?>, stateId: Int = 0)

    /**
     * Sent by the server when an chemdah in a slot (in a window) is added/removed.
     *
     * The window which is being updated. 0 for player inventory.
     * Note that all known window types include the player inventory.
     * This packet will only be sent for the currently opened window while the player is performing actions,
     * even if it affects the player inventory.
     * After the window is closed, a number of these packets are sent to update the player's inventory window (0).
     *
     * @param slot The slot that should be updated
     * @param itemStack The to update chemdah stack
     */
    fun sendWindowSetSlot(player: Player, containerId: Int, slot: Int, itemStack: ItemStack? = null, stateId: Int = -1)

    fun sendCursorItem(player: Player, itemStack: ItemStack? = null, stateId: Int = -1)

    fun sendWindowSetSlots(player: Player, containerId: Int, items: Map<Int,ItemStack?>)

    /**
     * This packet is used to inform the client that part of a GUI window should be updated.
     */
    fun sendWindowUpdateData(player: Player, containerId: Int, property: WindowProperty, value: Int)

    fun asCraftMirror(itemStack: Any): Any

    fun getContainerId(player: Player): Int

    /**
     * 获取玩家当前打开的容器ID
     * 用于检查玩家是否正在查看特定窗口
     */
    fun getActiveContainerId(player: Player): Int

}
