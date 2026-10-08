@file:OptIn(ExperimentalSerializationApi::class)

package cc.trixey.invero.core.menu

import cc.trixey.invero.core.Session
import cc.trixey.invero.core.animation.CycleMode
import cc.trixey.invero.core.animation.toCyclic
import cc.trixey.invero.core.util.session
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames

/**
 * Invero
 * cc.trixey.invero.core.menu.MenuTitle
 *
 * @author Arasple
 * @since 2023/1/25 11:36
 */
@Serializable
class MenuTitle(
    @JsonNames("mode")
    val type: CycleMode?,
    val period: Long?,
    val frames: List<Frame> = emptyList(),
    @SerialName("pre-generate")
    val preGenerate: Boolean?
) {

    @Serializable
    class Frame(
        @JsonNames("title")
        val value: String,
        @JsonNames("delay")
        val last: Long?
    )

    @Transient
    val isStatic = frames.size <= 1

    @Transient
    val default = frames.getOrElse(0) { Frame("Untitled", null) }.value

    fun submit(session: Session) {
        if (isStatic) return
        val cyclic = frames
            .map {
                if (preGenerate != false) Frame(session.parse(it.value), it.last ?: period ?: 20L)
                else it
            }
            .toCyclic(type ?: CycleMode.LOOP)

        fun loop(delay: Long) {
            session.taskGroup.launch(delay = delay) {
                if (session != session.viewer.session) return@launch
                if (session.hasVariable("title_task_running")) {
                    loop(20)
                    return@launch
                }
                cyclic.getAndCycle().apply {
                    session.window.title = if (preGenerate != false) value else session.parse(value)
                    loop(last ?: period ?: 20)
                }
            }
        }

        loop(0)
    }
}