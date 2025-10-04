package net.kigawa.keruta.executor.domain.request

import net.kigawa.keruta.executor.domain.err.KerutaErr

class RequestErr(val request: ApiRequest, val error: ReceiveTimeoutErr): KerutaErr() {
}
