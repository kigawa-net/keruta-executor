package net.kigawa.keruta.executor.domain.auth

import net.kigawa.keruta.executor.domain.err.KerutaErr
import net.kigawa.keruta.executor.domain.request.RequestErr

class AuthErr(val error: RequestErr): KerutaErr() {
}
