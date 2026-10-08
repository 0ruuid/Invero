package cc.trixey.invero.core.script.override

import cc.trixey.invero.common.message.sendFormattedAdventureComponent
import cc.trixey.invero.common.message.sendFormattedTabooComponent
import cc.trixey.invero.core.Context
import cc.trixey.invero.core.script.contextVar
import cc.trixey.invero.core.script.loader.InveroKetherParser
import cc.trixey.invero.core.script.parse
import cc.trixey.invero.core.script.player
import cc.trixey.invero.ui.bukkit.util.runOnEntity
import org.bukkit.Bukkit
import taboolib.module.kether.combinationParser

/**
 * Invero cc.trixey.invero.core.script.override.Actions
 *
 * @author Arasple
 * @since 2023/3/3 18:56
 */
@InveroKetherParser(["minimessage", "mmessage", "mmsg"], tags = ["kether-ext"])
fun actionTellMiniMessage() = combinationParser {
    it.group(
                    text(),
            )
            .apply(it) { message ->
                now {
                    val context = contextVar<Context?>("@context")?.variables ?: variables().toMap()
                    val player = player()

                    message.sendFormattedAdventureComponent(player, context)
                }
            }
}

@InveroKetherParser(["fluentmessage", "fmessage", "fmsg"], tags = ["kether-ext"])
fun actionTellFluentMessage() = combinationParser {
    it.group(
                    text(),
            )
            .apply(it) { message ->
                now {
                    val context = contextVar<Context?>("@context")?.variables ?: variables().toMap()
                    val player = player()

                    message.sendFormattedTabooComponent(player, context)
                }
            }
}

@InveroKetherParser(["broadcast", "bc"], tags = ["kether-ext"])
fun actionBroadcast() = combinationParser {
    it.group(text()).apply(it) { str ->
        now {
            val message = parse(str)
            Bukkit.getOnlinePlayers().forEach { player ->
                player.runOnEntity { player.sendMessage(message) }
            }
        }
    }
}
