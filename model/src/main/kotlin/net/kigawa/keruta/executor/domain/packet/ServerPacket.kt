package net.kigawa.keruta.executor.domain.packet

import net.kigawa.keruta.executor.domain.err.Res
import net.kigawa.keruta.executor.domain.err.TypeChangeErr
import net.kigawa.keruta.executor.domain.event.ApiEvent
import net.kigawa.keruta.executor.domain.request.ApiResponse

interface ServerPacket {
    val packetType: ServerPacketType
    fun asEvent(): Res<ApiEvent, TypeChangeErr>
    fun asResponse(): Res<ApiResponse, TypeChangeErr>
}
