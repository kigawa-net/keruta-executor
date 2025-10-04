package net.kigawa.keruta.executor.domain.client

interface ApiConnection {
    fun useReader(): ApiReader
    fun getSender(): ApiSender
}
