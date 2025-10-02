package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.receive.ApiEvent

interface ApiReceiveEventAction {
    fun receive(event: ApiEvent){}
}
