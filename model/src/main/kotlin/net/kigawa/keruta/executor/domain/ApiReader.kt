package net.kigawa.keruta.executor.domain

interface ApiReader {
    suspend fun read(): ApiReceiveData?
}
