package cc.trixey.invero.ui.bukkit.nms

import cc.trixey.invero.ui.bukkit.BukkitWindow
import cc.trixey.invero.ui.bukkit.InventoryPacket
import cc.trixey.invero.ui.bukkit.InventoryVanilla
import cc.trixey.invero.ui.bukkit.PlayerViewer
import cc.trixey.invero.ui.bukkit.api.notViewingWindow
import cc.trixey.invero.ui.bukkit.util.submitOnEntity
import dev.lone.itemsadder.api.FontImages.FontImageWrapper
import io.netty.util.internal.ConcurrentSet
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import taboolib.common.util.unsafeLazy
import taboolib.library.reflex.Reflex.Companion.setProperty
import taboolib.module.nms.MinecraftVersion.isUnobfuscated
import taboolib.module.nms.nmsProxy
import taboolib.module.nms.sendPacketBlocking

/**
 * Invero
 * cc.trixey.invero.ui.bukkit.nms.Handler
 *
 * @author Arasple
 * @since 2022/10/20
 */
const val persistContainerId: Int = 119
private val titleUpdating = ConcurrentSet<String>()

val handler by unsafeLazy {
    if (isUnobfuscated) NMSImplModern()
    else nmsProxy<NMS>()
}

/*
2023-1-20

菜单 A 高频更新 TITLE，则其在切换菜单 B 的过程中可能卡残影
且系统会认为菜单 B 容器是打开状态的
 */

fun PlayerViewer.setTitleUpdating(finished: Boolean = false) {
    if (!finished) titleUpdating.add(name)
    else titleUpdating.remove(name)
}

fun PlayerViewer.isTitleUpdating(): Boolean {
    return name in titleUpdating
}

fun BukkitWindow.updateTitle(title: String, updateInventory: Boolean = true) {
    val player = viewer.get<Player>() ?: return
    if (viewer.isTitleUpdating()) return
    else viewer.setTitleUpdating()

    val id = inventory.containerId
    val replaced = runCatching { FontImageWrapper.replaceFontImages(player, title) }.getOrNull() ?: title

    handler.sendWindowOpen(player, id, type, replaced)
    if (updateInventory && inventory is InventoryVanilla) {
        player.updateInventory()
    } else {
        (inventory as InventoryPacket).update()
    }

    // 补刀
    player.submitOnEntity(delay = 2L) {
        if (viewer.notViewingWindow() && player.openInventory.topInventory.type == InventoryType.CRAFTING) {
            handler.sendWindowClose(player, id)
        }
    }

    viewer.setTitleUpdating(true)
}

fun Player.sendCancelCoursor() {
    handler.sendWindowSetSlot(this, -1, -1, null, 1)
}

internal fun Player.postPacket(packet: Any, vararg fields: Pair<String, Any?>) = packet.apply {
    fields.forEach { (key, value) -> setProperty(key, value) }
    sendPacketBlocking(this)
    return this
}
