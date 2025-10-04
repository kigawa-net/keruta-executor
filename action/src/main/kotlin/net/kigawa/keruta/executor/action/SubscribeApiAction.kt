package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.client.ApiReader

interface SubscribeApiAction {
    val apiPacketReceiveAction: ApiPacketReceiveAction
    val closed: Boolean
    suspend fun subscribe(reader: ApiReader) {
        while (!closed) {
            val data = reader.read() ?: break
            apiPacketReceiveAction.receive(data)
        }
    }

}
