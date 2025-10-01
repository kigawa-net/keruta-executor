package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.CoroutineExecutor

interface SubscribeApiAction {
    val apiConnectAction: ApiConnectAction
    val coroutineExecutor: CoroutineExecutor
    fun subscribe() {
        coroutineExecutor.launch {
            val con = apiConnectAction.connect()
            val reader = con.getReader()
            val sender = con.getSender()
            while (true) {
                val data = reader.read() ?: break

            }
        }
    }
}
