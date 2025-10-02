package net.kigawa.keruta.executor.domain

import net.kigawa.keruta.executor.domain.receive.ApiPacket

interface ApiEventFlow {
    fun subscribe(block: suspend (ApiPacket) -> Unit)
}
