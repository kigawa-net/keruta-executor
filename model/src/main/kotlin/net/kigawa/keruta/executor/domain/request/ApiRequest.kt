package net.kigawa.keruta.executor.domain.request

import net.kigawa.keruta.executor.domain.client.ApiSendData
import java.util.UUID

interface ApiRequest: ApiSendData {
    val id: UUID
}
