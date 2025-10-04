package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.err.ErrNotifier
import net.kigawa.keruta.executor.domain.packet.ServerPacket
import net.kigawa.keruta.executor.domain.packet.ServerPacketType
import net.kigawa.keruta.executor.domain.request.ApiResponseNotifier

interface ApiPacketReceiveAction {
    val receiveEventAction: ApiReceiveEventAction
    val responseNotifier: ApiResponseNotifier
    val errNotifier: ErrNotifier
    suspend fun receive(packet: ServerPacket) {
        when (packet.packetType) {
            ServerPacketType.EVENT -> errNotifier.handleToNull { packet.asEvent() }
                ?.let { receiveEventAction.receive(it) }

            ServerPacketType.RESPONSE -> errNotifier.handleToNull { packet.asResponse() }
                ?.let { responseNotifier.notify(it) }
        }
    }
}
