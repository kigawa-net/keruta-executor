package net.kigawa.keruta.executor.domain.request

import net.kigawa.keruta.executor.domain.err.Res
import net.kigawa.keruta.executor.domain.err.TypeChangeErr
import java.util.*

interface ApiResponse {
    val id: UUID
    fun asAuthResponse(): Res<ApiAuthResponse, TypeChangeErr>
}
