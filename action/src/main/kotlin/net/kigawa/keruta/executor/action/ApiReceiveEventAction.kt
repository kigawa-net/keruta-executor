package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.event.ApiEvent

interface ApiReceiveEventAction {
    fun receive(event: ApiEvent){}
}
