package net.kigawa.keruta.executor.domain.client

import net.kigawa.keruta.executor.domain.packet.ServerPacket

interface ApiReader {
    suspend fun read(): ServerPacket?
}
