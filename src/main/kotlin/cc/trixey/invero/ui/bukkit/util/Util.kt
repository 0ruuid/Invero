package cc.trixey.invero.ui.bukkit.util

import java.net.URL

/**
 * Invero
 * cc.trixey.invero.ui.bukkit.util.Util
 *
 * @author Arasple
 * @since 2022/12/30 13:02
 */
fun fromURL(url: String): String {
    return try {
        String(URL(url).openStream().readBytes())
    } catch (t: Throwable) {
        ""
    }
}

inline fun synced(crossinline block: () -> Unit) {
    FoliaRuntime.runGlobal { block() }
}

fun Boolean.proceed(block: () -> Unit): Boolean {
    if (this) block()
    return this
}

fun Boolean.elseProceed(block: () -> Unit): Boolean {
    if (!this) block()
    return this
}

fun <T> T.proceed(condition: (T) -> Boolean, block: () -> Unit): T {
    if (condition(this)) block()
    return this
}

fun middle(pos1: Pair<Int, Int>, pos2: Pair<Int, Int>): Pair<Int, Int> {
    return (pos1.first + pos2.first) / 2 to (pos1.second + pos2.second) / 2
}

fun Double.format(): String {
    return String.format("%.2f", this)
}

fun Float.format(): String {
    return String.format("%.2f", this)
}