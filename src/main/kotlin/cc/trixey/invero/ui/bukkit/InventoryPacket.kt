package cc.trixey.invero.ui.bukkit

import cc.trixey.invero.ui.bukkit.nms.handler
import cc.trixey.invero.ui.bukkit.nms.persistContainerId
import cc.trixey.invero.ui.bukkit.util.runOnEntity
import cc.trixey.invero.ui.bukkit.util.submitOnEntity
import cc.trixey.invero.ui.common.event.ClickType
import dev.lone.itemsadder.api.FontImages.FontImageWrapper
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.info

/**
 * Invero
 * cc.trixey.invero.ui.bukkit.InventoryPacket
 *
 * @author Arasple
 * @since 2023/1/20 13:14
 */
class InventoryPacket(override val window: BukkitWindow) : ProxyBukkitInventory {

    override val hidePlayerInventory: Boolean
        get() = window.hidePlayerInventory

    override var containerId: Int = persistContainerId

    override fun isVirtual(): Boolean {
        return true
    }

    private var closed: Boolean = true
    private var stateId: Int = 0
    private var clickCallback: (slot: Int, type: ClickType) -> Boolean = { _, _ -> true }

    val windowItems = arrayOfNulls<ItemStack?>(containerType.entireWindowSize)

    fun updatePlayerItems(update: Boolean = false) {
        if (hidePlayerInventory) return
        val viewer = viewer ?: return

        windowItems.apply {
            viewer.copyStorage().forEachIndexed { index, itemStack ->
                val slot = containerSize + if (index < 9) index + 27 else index - 9
                this[slot] = itemStack
            }
        }
        if (update) update()
    }

    fun onClick(handler: (slot: Int, type: ClickType) -> Boolean): InventoryPacket {
        clickCallback = handler
        return this
    }

    override fun clear(slots: Collection<Int>) {
        slots.forEach { set(it, null) }
    }

    fun update() {
        val viewer = viewer ?: return
        handler.sendWindowItems(viewer, persistContainerId, windowItems.toList(), nextStateId())
    }

    fun update(vararg slot: Int) {
        val viewer = viewer ?: return

        slot.forEach {
            handler.sendWindowSetSlot(viewer, persistContainerId, it, windowItems[it], nextStateId())
        }
    }

    override fun get(slot: Int): ItemStack? {
        return windowItems[slot]
    }

    override fun set(slot: Int, itemStack: ItemStack?) {
        val viewer = viewer ?: return
        viewer.runOnEntity {
            try {
                windowItems[slot] = itemStack
            } catch (e: Throwable) {
                info("Failed to set slot $slot to $itemStack")
            }
            update(slot)
        }
    }

    override fun isViewing(): Boolean {
        return !closed
    }

    override fun open() {
        val viewer = viewer ?: return

        closed = false
        stateId = 0
        updatePlayerItems()
        val replaced =
            runCatching {
                FontImageWrapper.replaceFontImages(viewer, inventoryTitle)
            }.getOrNull() ?: inventoryTitle

        handler.sendWindowOpen(viewer, persistContainerId, containerType, replaced)

        update()

        // temp
        if (!hidePlayerInventory) {
            viewer.submitOnEntity(delay = 10L, period = 20L) {
                if (!window.isViewing()) {
                    cancel()
                    return@submitOnEntity
                }
                updatePlayerItems(true)
            }
        }
    }

    fun handleClickEvent(slot: Int, type: ClickType) {
        if (!clickCallback(slot, type)) return
        val pos = window.scale.convertToPosition(slot)

        window.panels.sortedByDescending { it.weight }.forEach {
            if (pos in it.area) {
                if (it.runClickCallbacks(pos, type, null)) {
                    it.handleClick(pos - it.locate, type, null)
                }
                return
            }
        }
    }

    fun resync() {
        val viewer = viewer ?: return
        handler.sendCursorItem(viewer, null, nextStateId())
        update()
    }

    private fun nextStateId(): Int {
        stateId = stateId + 1 and 32767
        return stateId
    }

}