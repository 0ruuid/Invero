package cc.trixey.invero.core.compat.generator

import cc.trixey.invero.common.AsyncElementGenerator
import cc.trixey.invero.common.Object
import cc.trixey.invero.common.sourceObject
import cc.trixey.invero.core.compat.DefGeneratorProvider
import cc.trixey.invero.core.geneartor.BaseGenerator
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.compat.generator.GeneratorWorlds
 *
 * @author Arasple
 * @since 2023/2/2 14:34
 */
@DefGeneratorProvider("world")
class GeneratorWorlds : BaseGenerator(), AsyncElementGenerator {

    override fun generate() {
        generated = snapshots()
    }

    override fun generateAsync(context: Any?): CompletableFuture<List<Object>> {
        return FoliaRuntime.supplyGlobal { snapshots() }
    }

    private fun snapshots(): List<Object> {
        return Bukkit.getWorlds().map {
            sourceObject {
                put("name", it.name)
                put("uid", it.uid)
                put("environment", it.environment.name)
                put("seed", it.seed)
                put("allowAnimals", it.allowAnimals)
                put("allowMonsters", it.allowMonsters)
                put("difficulty", it.difficulty)
                put("time", it.time)
                try {
                    put("minHeight", it.minHeight)
                    put("maxHeight", it.maxHeight)
                } catch (e: Throwable) {
                    // NOT SUPPORTED VERSION
                }
            }
        }
    }

}