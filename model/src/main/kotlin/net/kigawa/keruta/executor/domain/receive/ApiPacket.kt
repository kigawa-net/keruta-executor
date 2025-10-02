package net.kigawa.keruta.executor.domain.receive

interface ApiPacket {
    val packetType: ApiPacketType
    fun asEvent(): ApiEvent
    fun asResponse(): ApiResponse
}
