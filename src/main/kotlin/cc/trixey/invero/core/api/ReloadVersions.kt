package cc.trixey.invero.core.api

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 异步菜单重载的版本管理。 */
internal class ReloadVersions {

    private val generation = AtomicLong()
    private val sequence = AtomicLong()
    private val files = ConcurrentHashMap<String, Long>()

    data class Full(val generation: Long, val files: Map<String, Long>)

    data class Change(val generation: Long, val revision: Long)

    fun beginReload(): Full {
        return Full(generation.incrementAndGet(), files.toMap())
    }

    fun beginChange(path: String): Change {
        val epoch = generation.get()
        return Change(epoch, files.compute(path) { _, _ -> sequence.incrementAndGet() }!!)
    }

    fun isCurrent(ticket: Full): Boolean {
        return ticket.generation == generation.get()
    }

    fun isCurrent(ticket: Full, path: String): Boolean {
        return isCurrent(ticket) && files[path] == ticket.files[path]
    }

    fun isCurrent(ticket: Change, path: String): Boolean {
        return ticket.generation == generation.get() && files[path] == ticket.revision
    }

    fun invalidate() {
        generation.incrementAndGet()
    }
}
