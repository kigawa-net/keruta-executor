package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.receive.ApiResponseError

interface ApiReceiveResponseErrorAction {
    fun receive(error: ApiResponseError)
}
