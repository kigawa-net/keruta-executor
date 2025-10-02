package net.kigawa.keruta.executor.domain.receive

interface ApiResponse {
    val type: ApiResponseType
    fun asSuccess(): ApiResponseSuccess
    fun asError(): ApiResponseError
}
