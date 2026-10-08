@file:OptIn(ExperimentalSerializationApi::class)

package cc.trixey.invero.core.panel

import cc.trixey.invero.common.Object
import cc.trixey.invero.common.AsyncElementGenerator
import cc.trixey.invero.common.ElementGenerator
import cc.trixey.invero.common.util.prettyPrint
import cc.trixey.invero.core.*
import cc.trixey.invero.core.icon.Icon
import cc.trixey.invero.core.serialize.MappedIconSerializer
import cc.trixey.invero.core.serialize.PosSerializer
import cc.trixey.invero.core.serialize.ScaleSerializer
import cc.trixey.invero.core.util.KetherHandler
import cc.trixey.invero.core.util.session
import cc.trixey.invero.ui.bukkit.PanelContainer
import cc.trixey.invero.ui.bukkit.api.dsl.generatorPaged
import cc.trixey.invero.ui.bukkit.panel.PagedGeneratorPanel
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.common.Pos
import cc.trixey.invero.ui.common.Scale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames
import org.bukkit.entity.Player
import taboolib.common5.cbool
import java.util.concurrent.CompletableFuture

/**
 * Invero
 * cc.trixey.invero.core.panel.PanelGenerator
 *
 * @author Arasple
 * @since 2023/1/29 16:25
 */
@Serializable
class PanelGenerator(
    @Serializable(with = ScaleSerializer::class)
    @SerialName("scale")
    private val _scale: Scale?,
    override val layout: Layout?,
    @Serializable(with = PosSerializer::class)
    override val locate: Pos?,
    @SerialName("generator")
    val settings: StructureGenerator,
    @JsonNames("icon", "item", "items")
    @Serializable(with = MappedIconSerializer::class)
    override val icons: Map<String, Icon> = emptyMap()
) : AgentPanel(), IconContainer {

    init {
        registerIcon(settings.output, "output")
        registerIcons()
    }

    @Transient
    override val scale = _scale ?: layout?.getScale() ?: Scale(9 to -1)

    override fun invoke(parent: PanelContainer, session: Session) =
        parent.generatorPaged<Any>(scale.pair, parent.locate()) {
            skipRender = true
            // 生成默认图标
            val def = icons.map { (_, icon) ->
                icon.invoke(session, this@PanelGenerator, this@generatorPaged, renderNow = false)
            }
            // 过滤元素
            if (settings.filter != null) {
                filter(session, this@generatorPaged, settings.filter)
                session.setVariable("@raw_filter", settings.filter)
            }
            generatorSource { emptyList() }
            // 生成输出
            generatorOutput {
                settings.output.invoke(session, this@PanelGenerator, this, (it as Object).variables)
            }
            generate(session).whenComplete { generated, throwable ->
                session.taskGroup.launch {
                    if (session != session.viewer.session) return@launch
                    if (throwable != null) {
                        throwable.prettyPrint()
                        return@launch
                    }
                    generatorSource { generated }
                    def.forEach {
                        it.relocate()
                        it.render()
                    }
                    render()
                    session.taskGroup.launch(delay = 2L) {
                        def.forEach {
                            if (it.relocate()) it.renderItem()
                        }
                        (session.menu as? BaseMenu)?.updateTitle(session)
                    }
                }
            }
        }

    fun filter(session: Session, panel: PagedGeneratorPanel<*>, filter: String) {
        val viewer = session.viewer.get<Player>()
        panel.filterBy {
            KetherHandler
                .invoke(filter, viewer, session.getVariables(ext = (it as Object).variables))
                .getNow(true)
                .cbool
        }
    }

    private fun generate(session: Session): CompletableFuture<List<Object>> {
        // 将 data 属性传递到 Context 的 extVars 中，以便生成器可以访问
        val extVars = settings.data?.toMap() ?: emptyMap()
        val context = Context(session.viewer, session, extVars = extVars)
        val created = settings.create()
        val future = if (FoliaRuntime.isFolia && created is AsyncElementGenerator) {
            created.generateAsync(context)
        } else {
            val result = CompletableFuture<List<Object>>()
            runCatching {
                created.generate(context)
                created.generated ?: emptyList()
            }.onSuccess(result::complete).onFailure(result::completeExceptionally)
            result
        }
        return future.thenApply { source ->
            finishGeneration(created, source)
        }
    }

    private fun finishGeneration(created: ElementGenerator, source: List<Object>): List<Object> {
        return created.apply {
            generated = source
            if (settings.extenedObjects != null) {
                generated = generated.orEmpty() + settings.extenedObjects
            }
            if (settings.extenedProperties != null) {
                generated = generated?.map {
                    val content = it.content.toMutableMap()
                    settings.extenedProperties.forEach { (key, value) -> content[key] = "ext@$value" }
                    Object(content)
                }
            }
            if (settings.sortBy != null) {
                sortBy { it[settings.sortBy].toString() }
            }
        }.generated.orEmpty()
    }

}