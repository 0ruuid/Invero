package cc.trixey.invero.core.compat.generator

import cc.trixey.invero.common.AsyncElementGenerator
import cc.trixey.invero.common.sourceObject
import cc.trixey.invero.core.Context
import cc.trixey.invero.core.compat.DefGeneratorProvider
import cc.trixey.invero.core.geneartor.ContextGenerator
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.bukkit.util.supplyOnEntity
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.compat.generator.GeneratorEntities
 *
 * @author Arasple
 * @since 2023/2/27 7:55
 */

@DefGeneratorProvider("entity_world")
internal class GeneratorEntitiesWorld : ContextGenerator(), AsyncElementGenerator {

    override fun generate(context: Context) {
        val player = context.viewer.get<Player>() ?: return
        generated = player
            .world
            .entities
            .map {
                sourceObject {
                    put("name", it.name)
                    put("type", it.type.name)
                }
            }
    }

    override fun generateAsync(context: Any?): CompletableFuture<List<cc.trixey.invero.common.Object>> {
        val menuContext = context as Context
        val player = menuContext.viewer.get<Player>() ?: return CompletableFuture.completedFuture(emptyList())
        val world = player.world
        return FoliaRuntime.supplyGlobal { world.loadedChunks.toList() }.thenCompose { chunks ->
            val futures = chunks.map { chunk ->
                val location = Location(world, (chunk.x shl 4) + 8.0, 64.0, (chunk.z shl 4) + 8.0)
                FoliaRuntime.supplyLocation(location) {
                    chunk.entities.map {
                        sourceObject {
                            put("name", it.name)
                            put("type", it.type.name)
                        }
                    }
                }.exceptionally { emptyList() }
            }
            CompletableFuture.allOf(*futures.toTypedArray()).thenApply {
                futures.flatMap { it.getNow(emptyList()) }
            }
        }
    }

}

@DefGeneratorProvider("entity_nearby")
internal class GeneratorEntitiesNearby : ContextGenerator(), AsyncElementGenerator {

    override fun generate(context: Context) {
        val player = context.viewer.get<Player>() ?: return
        generated = player
            .getNearbyEntities(10.0, 10.0, 10.0)
            .map {
                sourceObject {
                    put("name", it.name)
                    put("type", it.type.name)
                }
            }
    }

    override fun generateAsync(context: Any?): CompletableFuture<List<cc.trixey.invero.common.Object>> {
        val menuContext = context as Context
        val player = menuContext.viewer.get<Player>() ?: return CompletableFuture.completedFuture(emptyList())
        return player.supplyOnEntity {
            player.getNearbyEntities(10.0, 10.0, 10.0).map {
                sourceObject {
                    put("name", it.name)
                    put("type", it.type.name)
                }
            }
        }.exceptionally { emptyList() }
    }

}