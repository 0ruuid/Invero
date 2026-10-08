package cc.trixey.invero.core.script

import cc.trixey.invero.core.Context
import cc.trixey.invero.core.compat.bungeecord.Bungees
import cc.trixey.invero.core.compat.eco.HookPlayerPoints
import cc.trixey.invero.core.script.loader.InveroKetherParser
import cc.trixey.invero.common.message.translateFormattedMessage
import cc.trixey.invero.ui.bukkit.util.FoliaRuntime
import cc.trixey.invero.ui.bukkit.util.runOnEntity
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common5.cdouble
import taboolib.common5.cint
import taboolib.library.kether.ParsedAction
import taboolib.module.kether.*
import taboolib.platform.compat.depositBalance
import taboolib.platform.compat.getBalance
import taboolib.platform.compat.withdrawBalance
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

/**
 * Invero
 * cc.trixey.invero.core.script.Actions
 *
 * @author Arasple
 * @since 2023/1/24 22:51
 */
@InveroKetherParser(["parse"])
internal fun actionParser() = scriptParser {
    val str = it.nextParsedAction()
    actionTake {
        run(str).str { s ->
            val context = contextVar<Context?>("@context")?.variables ?: variables().toMap()
            val player = player()

            s.translateFormattedMessage(player, context)
        }
    }
}

/*
connect <serverName> for <playerName>
 */
@InveroKetherParser(["connect", "bungee"])
internal fun actionConnect() = combinationParser {
    it.group(
        text(),
        command("for", then = action()).option().defaultsTo(null)
    ).apply(it) { server, player ->
        future {
            val result = CompletableFuture<Any?>()
            if (player == null) {
                val target = session()?.viewer?.get<Player>() ?: player()
                target.runOnEntity { result.complete(Bungees.connect(target, server)) }
            } else {
                newFrame(player).run<Any>().thenAccept { playerId ->
                    FoliaRuntime.supplyGlobal { Bukkit.getPlayerExact(playerId.toString()) }.thenAccept { target ->
                        if (target == null) result.complete(null)
                        else target.runOnEntity { result.complete(Bungees.connect(target, server)) }
                    }
                }
            }
            result
        }
    }
}

/**
 * eco
 * eco get
 * eco take 200
 * eco give 200
 * eco set 200
 */
@InveroKetherParser(["eco", "money", "vault"])
internal fun actionEco() = scriptParser {
    if (!it.hasNext()) actionNow { player().getBalance() }
    else {
        val method = it.nextToken()
        if (method == null || method == "get" || method == "balance") actionNow { player().getBalance() }
        else {
            val parsedAction = it.nextParsedAction()
            actionNow {
                val amount = newFrame(parsedAction).run<String>().getNow("0").cdouble
                when (method) {
                    "has" -> player().getBalance() >= amount
                    "take" -> player().withdrawBalance(amount)
                    "give" -> player().depositBalance(amount)
                    "set" -> {
                        val currentBalance = player().getBalance()
                        if (currentBalance > amount) {
                            player().withdrawBalance(currentBalance - amount)
                        } else if (currentBalance < amount) {
                            player().depositBalance(amount - currentBalance)
                        }
                        true
                    }
                    else -> error("Unknown eco method: $method")
                }
            }
        }
    }
}

@InveroKetherParser(["playerpoints", "points", "point"])
internal fun actionPoints() = scriptParser {
    if (!it.hasNext()) actionNow { HookPlayerPoints.look(player()) }
    else {
        val method = it.nextToken()
        if (method == null || method == "get" || method == "balance") actionNow {
            HookPlayerPoints.look(player()) ?: -1
        }
        else {
            val parsedAction = it.nextParsedAction()
            actionNow {
                val amount = newFrame(parsedAction).run<String>().getNow("0").cint
                when (method) {
                    "has" -> (HookPlayerPoints.look(player()) ?: 0) >= amount
                    "take" -> HookPlayerPoints.take(player(), amount)
                    "give" -> HookPlayerPoints.add(player(), amount)
                    "set" -> {
                        val currentPoints = HookPlayerPoints.look(player()) ?: 0
                        if (currentPoints > amount) {
                            HookPlayerPoints.take(player(), currentPoints - amount)
                        } else if (currentPoints < amount) {
                            HookPlayerPoints.add(player(), amount - currentPoints)
                        }
                        true
                    }
                    else -> error("Unknown eco method: $method")
                }
            }
        }
    }
}

/**
 * 示例: 
 * max 1 10       -> 返回10
 * max [ 1 7 9 ]  -> 返回9
 */
@InveroKetherParser(["max"])
internal fun actionMax() = scriptParser {
    val arrayMode = it.hasNext() && it.nextToken() == "["
    
    // 双值或数组收集
    val values = mutableListOf<ParsedAction<*>>()
    
    if (arrayMode) {
        // 数组形式 - 收集所有值直到遇到结束符号 ]
        while (it.hasNext()) {
            it.mark()
            if (it.nextToken() == "]") {
                break
            }
            it.reset()
            values.add(it.nextParsedAction())
        }
    } else {
        // 双值形式
        if (it.hasNext()) {
            values.add(it.nextParsedAction())
            if (it.hasNext()) {
                values.add(it.nextParsedAction())
            }
        }
    }
    
    actionNow {
        if (values.isEmpty()) {
            return@actionNow 0.0
        }
        
        val results = mutableListOf<Double>()
        for (action in values) {
            try {
                val result = newFrame(action).run<Any>().getNow(null)
                if (result != null) {
                    val doubleValue = when (result) {
                        is Number -> result.toDouble()
                        is String -> result.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    results.add(doubleValue)
                }
            } catch (e: Exception) {
                // 忽略错误值
            }
        }
        
        if (results.isEmpty()) 0.0 else results.maxOrNull() ?: 0.0
    }
}

/**
 * 示例:
 * min 1 10       -> 返回1
 * min [ 1 7 9 ]  -> 返回1
 */
@InveroKetherParser(["min"])
internal fun actionMin() = scriptParser {
    val arrayMode = it.hasNext() && it.nextToken() == "["
    
    // 双值或数组收集
    val values = mutableListOf<ParsedAction<*>>()
    
    if (arrayMode) {
        // 数组形式 - 收集所有值直到遇到结束符号 ]
        while (it.hasNext()) {
            it.mark()
            if (it.nextToken() == "]") {
                break
            }
            it.reset()
            values.add(it.nextParsedAction())
        }
    } else {
        // 双值形式
        if (it.hasNext()) {
            values.add(it.nextParsedAction())
            if (it.hasNext()) {
                values.add(it.nextParsedAction())
            }
        }
    }
    
    actionNow {
        if (values.isEmpty()) {
            return@actionNow 0.0
        }
        
        val results = mutableListOf<Double>()
        for (action in values) {
            try {
                val result = newFrame(action).run<Any>().getNow(null)
                if (result != null) {
                    val doubleValue = when (result) {
                        is Number -> result.toDouble()
                        is String -> result.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    results.add(doubleValue)
                }
            } catch (e: Exception) {
                // 忽略错误值
            }
        }
        
        if (results.isEmpty()) 0.0 else results.minOrNull() ?: 0.0
    }
}