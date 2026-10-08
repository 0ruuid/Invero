package cc.trixey.invero.core.icon

import cc.trixey.invero.core.AgentPanel
import cc.trixey.invero.core.Context
import cc.trixey.invero.core.Session
import cc.trixey.invero.core.animation.Cyclic
import cc.trixey.invero.core.item.Frame
import cc.trixey.invero.core.item.renderFor
import cc.trixey.invero.core.util.session
import cc.trixey.invero.ui.bukkit.api.dsl.set
import cc.trixey.invero.ui.bukkit.element.item.SimpleItem
import cc.trixey.invero.ui.common.Panel

/**
 * Invero
 * cc.trixey.invero.core.icon.IconElement
 *
 * @author Arasple
 * @since 2023/1/16 12:22
 */
open class IconElement(
    val session: Session,
    val icon: Icon,
    val agent: AgentPanel,
    panel: Panel,
    vars: Map<String, Any>
) : SimpleItem(panel) {

    private val paused = arrayOf(true, true, true)

    internal val context by lazy { Context(session.viewer, session, panel, this, vars) }

    var iconIndex = relocateIndex()

    private var frame: Frame? = null
        set(value) {
            value?.renderFor(agent.scale, this, currentIcon.defaultFrame)
            field = value
        }

    private var framesDefaultDelay: Long = icon.framesProperties?.defaultDelay ?: 20

    private var framesCyclic: Cyclic<Frame>? = null
        set(value) {
            val previousIndex = field?.index
            field = value
            if (value != null && !value.isSingle()) submitFrameTask(previousIndex)
        }

    fun invoke() {
        agent.layout
            ?.search(icon.id)
            ?.let { this@IconElement.set(it) }
    }

    fun render() {
        // 默认帧
        frame = currentIcon.defaultFrame
        // 循环物品帧
        framesCyclic = currentIcon.generateCyclicFrames()
        // 周期任务：翻译物品帧的相关变量
        if (icon.periodUpdate > 0) {
            session.taskGroup.launch(delay = 10L, period = icon.periodUpdate) {
                if (isVisible() && paused[0]) renderItem()
            }
        }
        // 周期任务：重定向子图标
        if (icon.periodRelocate > 0 && !icon.subIcons.isNullOrEmpty()) {
            session.taskGroup.launch(delay = 10L, period = icon.periodRelocate) {
                if (isVisible() && paused[1]) relocate()
            }
        }
        // 交互逻辑
        onClick { type, _ -> currentHandler?.run(context, type) }
    }

    /**
     * 提交动画循环任务
     */
    private fun submitFrameTask(inherit: Int?) {
        val frames = framesCyclic!!
        if (inherit != null && inherit <= frames.maxIndex) frames.index = inherit

        fun loop(delay: Long) {
            session.taskGroup.launch(delay = delay) {
                if (frames != framesCyclic || frames.isAnimationEnded() || shouldUnregister()) return@launch
                if (isVisible() && paused[2]) {
                    frame = frames.getAndCycle()
                    loop(frame?.delay ?: framesDefaultDelay)
                }
            }
        }

        loop(0)
    }

    /**
     * 渲染图标的物品
     */
    fun renderItem() {
        frame?.renderFor(agent.scale, this, currentIcon.defaultFrame, setSlot = false)
    }

    /**
     * 重新定位子图标
     */
    fun relocate(): Boolean {
        if (icon.subIcons.isNullOrEmpty()) return false

        val previousIndex = iconIndex
        val relocatedIndex = relocateIndex()

        if (previousIndex >= 0 && relocatedIndex < 0) {
            iconIndex = -1
            frame = icon.defaultFrame
            framesDefaultDelay = icon.framesProperties?.defaultDelay ?: 20L
            framesCyclic = icon.generateCyclicFrames()
            return true
        } else if (relocatedIndex >= 0 && relocatedIndex != previousIndex) {
            val subIcon = icon.subIcons[relocatedIndex]
            iconIndex = relocatedIndex
            frame = subIcon.defaultFrame
            framesCyclic = subIcon.generateCyclicFrames()
            framesDefaultDelay = subIcon.framesProperties?.defaultDelay ?: 20L
            return true
        }
        return false
    }

    fun relocateIndex(): Int {
        return icon.subIcons?.indexOfFirst { it.condition?.evalInstant(context) ?: false } ?: -1
    }

    fun pauseUpdateTask() {
        paused[0] = false
    }

    fun pauseRelocateTask() {
        paused[1] = false
    }

    fun pauseFramesTask() {
        paused[2] = false
    }

    fun resumeUpdateTask() {
        paused[0] = true
    }

    fun resumeRelocateTask() {
        paused[1] = true
    }

    fun resumeFramesTask() {
        paused[2] = true
    }

    /**
     * 取得有效的交互处理器
     */
    val currentHandler: IconHandler?
        get() = currentIcon.handler

    /**
     * 取得有效图标
     */
    val currentIcon: Icon
        get() = icon.subIcons?.getOrNull(iconIndex) ?: icon

    /**
     * 是否需要注销
     */
    fun shouldUnregister(): Boolean {
        return session != session.viewer.session
    }

}