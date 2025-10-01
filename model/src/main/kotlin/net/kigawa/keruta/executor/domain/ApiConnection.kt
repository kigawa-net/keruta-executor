package net.kigawa.keruta.executor.domain

interface ApiConnection {
    fun getReader(): ApiReader
    fun getSender(): ApiSender
}
