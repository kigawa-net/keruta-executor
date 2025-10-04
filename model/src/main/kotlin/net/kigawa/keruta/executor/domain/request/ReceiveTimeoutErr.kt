package net.kigawa.keruta.executor.domain.request

import net.kigawa.keruta.executor.domain.err.KerutaErr
import java.util.UUID
import kotlin.time.Duration

class ReceiveTimeoutErr(val id: UUID, val timeout: Duration): KerutaErr() {
}
