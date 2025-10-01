package net.kigawa.keruta.executor.domain

interface CoroutineExecutor {
    fun launch(block: suspend () -> Unit): CoroutineJob
}
