package net.kigawa.keruta.executor.domain.request

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import net.kigawa.keruta.executor.domain.Config
import net.kigawa.keruta.executor.domain.client.ApiSender
import net.kigawa.keruta.executor.domain.err.Res

class ApiRequestor(
    val sender: ApiSender,
    val responseNotifier: ApiResponseNotifier,
    val config: Config,
) {
    suspend fun send(request: ApiRequest): Res<ApiResponse, RequestErr> {
        val deff = CoroutineScope(currentCoroutineContext()).async {
            responseNotifier.receive(request.id, config.timeout)
        }
        sender.send(request)
        return when (val res = deff.await()) {
            is Res.Success -> Res.Success(res.value)
            is Res.Error -> Res.Error(RequestErr(request, res.error))
        }
    }
}
