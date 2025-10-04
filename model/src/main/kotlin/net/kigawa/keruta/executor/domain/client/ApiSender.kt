package net.kigawa.keruta.executor.domain.client

interface ApiSender {
    suspend fun send(data: ApiSendData)
}
