package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.ApiResponseNotifier
import net.kigawa.keruta.executor.domain.receive.ApiPacket
import net.kigawa.keruta.executor.domain.receive.ApiPacketType

interface ApiPacketReceiveAction {
    val receiveEventAction: ApiReceiveEventAction
    val responseNotifier: ApiResponseNotifier
    suspend fun receive(packet: ApiPacket) {
        when (packet.packetType) {
            ApiPacketType.EVENT -> receiveEventAction.receive(packet.asEvent())
            ApiPacketType.RESPONSE -> responseNotifier.notify(packet.asResponse())
        }
    }
}
