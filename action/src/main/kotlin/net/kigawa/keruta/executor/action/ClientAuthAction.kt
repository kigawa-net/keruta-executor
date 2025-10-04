package net.kigawa.keruta.executor.action

import net.kigawa.keruta.executor.domain.auth.ApiRequestAuth
import net.kigawa.keruta.executor.domain.auth.AuthErr
import net.kigawa.keruta.executor.domain.err.Res
import net.kigawa.keruta.executor.domain.request.ApiRequestor
import net.kigawa.keruta.executor.domain.request.ApiResponse

interface ClientAuthAction {
    val apiRequestor: ApiRequestor
    suspend fun authenticate(): Res<ApiResponse, AuthErr> {
        return when (val res = apiRequestor.send(ApiRequestAuth())) {
            is Res.Success -> Res.Success(res.value)
            is Res.Error -> Res.Error(AuthErr(res.error))
        }

    }
}
