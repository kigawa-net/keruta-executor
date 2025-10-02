package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.receive.ApiResponseSuccess

interface ApiReceiveResponseSuccessAction {
    fun receive(response: ApiResponseSuccess)
}
