package cc.trixey.invero.core.compat.activators

/** 菜单命令注册项的差量更新。 */
internal class CommandBindings<H> {

    private data class Entry<H>(
        var definition: CommandStructure,
        val handle: H,
        var needsUpdate: Boolean = false
    )

    private val entries = linkedMapOf<String, Entry<H>>()

    val handles: List<H>
        get() = entries.values.map { it.handle }

    fun reconcile(
        definitions: List<CommandStructure>,
        create: (CommandStructure) -> H,
        update: (H, CommandStructure) -> Unit,
        remove: (H) -> Unit
    ): Boolean {
        val desired = definitions.associateBy { it.name }
        require(desired.size == definitions.size) { "Duplicate command names" }
        var changed = false
        entries.keys.toList().forEach { name ->
            val entry = entries.getValue(name)
            val next = desired[name]
            if (next == null || entry.definition.aliases != next.aliases) {
                entry.needsUpdate = true
                remove(entry.handle)
                if (next == null) entries.remove(name)
                changed = true
            }
        }
        desired.forEach { (name, definition) ->
            val entry = entries[name]
            if (entry == null) {
                entries[name] = Entry(definition, create(definition))
                changed = true
            } else if (entry.needsUpdate || entry.definition != definition) {
                entry.needsUpdate = true
                update(entry.handle, definition)
                entry.definition = definition
                entry.needsUpdate = false
                changed = true
            }
        }
        return changed
    }

    fun clear(remove: (H) -> Unit): Boolean {
        if (entries.isEmpty()) return false
        entries.values.forEach { remove(it.handle) }
        entries.clear()
        return true
    }
}

/** 全局线程上的命令树刷新合并器。 */
internal class CommandRefresh(
    private val schedule: (() -> Unit) -> Unit,
    private val refresh: () -> Unit
) {

    private var pending = false
    private var stopped = false

    fun request() {
        if (pending || stopped) return
        pending = true
        schedule {
            pending = false
            if (!stopped) refresh()
        }
    }

    fun stop() {
        stopped = true
    }
}
