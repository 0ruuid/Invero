package cc.trixey.invero.common

import java.util.concurrent.CompletableFuture

interface AsyncElementGenerator : ElementGenerator {

    fun generateAsync(context: Any? = null): CompletableFuture<List<Object>>
}
