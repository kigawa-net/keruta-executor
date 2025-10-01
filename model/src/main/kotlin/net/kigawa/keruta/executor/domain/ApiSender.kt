package net.kigawa.keruta.executor.domain

interface ApiSender {
    suspend fun send(data: ApiSendData)
}
