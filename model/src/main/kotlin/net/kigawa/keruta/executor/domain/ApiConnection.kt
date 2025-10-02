package net.kigawa.keruta.executor.domain

interface ApiConnection {
    fun useReader(): ApiReader
    fun getSender(): ApiSender
}
