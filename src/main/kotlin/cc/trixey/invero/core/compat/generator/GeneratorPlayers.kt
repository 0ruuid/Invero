package cc.trixey.invero.core.compat.generator

import cc.trixey.invero.common.AsyncElementGenerator
import cc.trixey.invero.common.Object
import cc.trixey.invero.common.sourceObject
import cc.trixey.invero.core.compat.DefGeneratorProvider
import cc.trixey.invero.core.geneartor.BaseGenerator
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.bukkit.util.supplyOnEntity
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.platform.util.onlinePlayers
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.compat.generator.GeneratorPlayers
 *
 * @author Arasple
 * @since 2023/1/29 22:03
 */
@DefGeneratorProvider("player")
class GeneratorPlayers : BaseGenerator(), AsyncElementGenerator {

    override fun generate() {
        generated = onlinePlayers.map { snapshot(it, includeInstance = true) }
    }

    override fun generateAsync(context: Any?): CompletableFuture<List<Object>> {
        return FoliaRuntime.supplyGlobal { Bukkit.getOnlinePlayers().toList() }.thenCompose { players ->
            val futures = players.map { player ->
                player.supplyOnEntity {
                    if (player.isOnline) snapshot(player, includeInstance = false) else null
                }.exceptionally { null }
            }
            CompletableFuture.allOf(*futures.toTypedArray()).thenApply {
                futures.mapNotNull { it.getNow(null) }
            }
        }
    }

    private fun snapshot(player: Player, includeInstance: Boolean): Object {
        val location = player.location
        return sourceObject {
            if (includeInstance) put("instance", player)
            put("name", player.name)
            put("uuid", player.uniqueId.toString())
            put("displayName", player.displayName)
            put("isSneaking", player.isSneaking)
            put("isSprinting", player.isSprinting)
            put("x", location.x)
            put("y", location.y)
            put("z", location.z)
            put("yaw", location.yaw)
            put("pitch", location.pitch)
            put("address", player.address?.hostString)
        }
    }

}