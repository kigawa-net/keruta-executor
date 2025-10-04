package net.kigawa.keruta.executor.domain.auth

import net.kigawa.keruta.executor.domain.request.ApiRequest
import java.util.UUID

class ApiRequestAuth: ApiRequest {
    override val id: UUID = UUID.randomUUID()
}
