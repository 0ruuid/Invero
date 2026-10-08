package cc.trixey.invero.ui.bukkit.nms

import cc.trixey.invero.common.message.Message
import cc.trixey.invero.ui.common.ContainerType
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minecraft.network.chat.Component as NMSComponent
import net.minecraft.network.protocol.game.*
import net.minecraft.world.inventory.MenuType
import org.bukkit.Material
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.reflex.Reflex.Companion.getProperty
import taboolib.library.reflex.Reflex.Companion.setProperty
import taboolib.library.reflex.Reflex.Companion.unsafeInstance
import taboolib.module.nms.MinecraftVersion.versionId
import taboolib.module.nms.NMSItemTag
import taboolib.module.nms.sendBundlePacketBlocking
import taboolib.module.nms.sendPacketBlocking

/**
 * Invero
 * cc.trixey.invero.ui.bukkit.nms.NMSImplModern
 *
 * Mojang-mapped NMS implementation for Paper 26.1+ (unobfuscated).
 * Uses direct class references — no ASM remapping needed.
 *
 * @author Arasple
 * @since 2025/6/5
 */
class NMSImplModern : NMS {

    private val itemAir: Any? by lazy { null.asNMSCopy() }

    override fun sendWindowOpen(player: Player, containerId: Int, type: ContainerType, rawTitle: String) {
        // Process MiniMessage via Invero's Adventure, convert to legacy § string, then
        // create NMS Component via server's API — avoids Adventure version mismatch.
        val component = Message.parseAdventure(rawTitle)
        val legacy = LegacyComponentSerializer.legacySection().serialize(component)

        val packet = ClientboundOpenScreenPacket::class.java.unsafeInstance()
        val windowType: Any = if (versionId < 11900) {
            type.serialId
        } else {
            MenuType::class.java.getProperty<Any>(type.vanillaId, true)!!
        }
        player.postPacket(packet, "containerId" to containerId, "type" to windowType, "title" to NMSComponent.literal(legacy))
    }

    override fun sendWindowClose(player: Player, containerId: Int) {
        player.sendPacketBlocking(ClientboundContainerClosePacket(containerId))
    }

    override fun sendWindowItems(player: Player, containerId: Int, itemStacks: List<ItemStack?>, stateId: Int) {
        val packet = ClientboundContainerSetContentPacket::class.java.unsafeInstance()
        val items = itemStacks.asNMSCopy()
        player.postPacket(
            packet,
            "containerId" to containerId,
            "items" to items,
            "carriedItem" to itemAir,
            "stateId" to stateId,
        )
    }

    override fun sendWindowSetSlot(player: Player, containerId: Int, slot: Int, itemStack: ItemStack?, stateId: Int) {
        val packet = ClientboundContainerSetSlotPacket::class.java.unsafeInstance()
        player.postPacket(
            packet,
            "containerId" to containerId,
            "slot" to slot,
            "itemStack" to itemStack.asNMSCopy(),
            "stateId" to stateId,
        )
    }

    override fun sendCursorItem(player: Player, itemStack: ItemStack?, stateId: Int) {
        if (versionId >= 12104) {
            player.postPacket(
                ClientboundSetCursorItemPacket::class.java.unsafeInstance(),
                "contents" to itemStack.asNMSCopy()
            )
        } else {
            sendWindowSetSlot(player, -1, -1, itemStack, stateId)
        }
    }

    override fun sendWindowSetSlots(player: Player, containerId: Int, items: Map<Int, ItemStack?>) {
        val packets = items.map { (slot, itemStack) ->
            ClientboundContainerSetSlotPacket::class.java.unsafeInstance().also {
                it.setProperty("containerId", containerId)
                it.setProperty("slot", slot)
                it.setProperty("itemStack", itemStack.asNMSCopy())
                it.setProperty("stateId", 1)
            }
        }
        player.sendBundlePacketBlocking(packets)
    }

    override fun sendWindowUpdateData(player: Player, containerId: Int, property: WindowProperty, value: Int) {
        player.sendPacketBlocking(
            ClientboundContainerSetDataPacket(containerId, property.index, value)
        )
    }

    override fun asCraftMirror(itemStack: Any): ItemStack {
        return NMSItemTag.asBukkitCopy(itemStack)
    }

    override fun getContainerId(player: Player): Int {
        val cp = player as CraftPlayer
        val handle = cp.handle
        return handle.getProperty<Any>("containerMenu")?.getProperty<Int>("containerId") ?: -1
    }

    override fun getActiveContainerId(player: Player): Int {
        if (!player.isOnline) return -1
        return try {
            val cp = player as CraftPlayer
            val handle = cp.handle
            handle.getProperty<Any>("containerMenu")?.getProperty<Int>("containerId") ?: -1
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun ItemStack?.asNMSCopy(): Any? {
        return NMSItemTag.asNMSCopy(this ?: ItemStack(Material.AIR))
    }

    private fun List<ItemStack?>.asNMSCopy(): List<Any?> {
        return map { it.asNMSCopy() }
    }

}
