package net.kigawa.keruta.executor.domain.request

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import net.kigawa.keruta.executor.domain.err.Res
import java.util.*
import kotlin.time.Duration

class ApiResponseNotifier {
    private val responseFlow = MutableSharedFlow<ApiResponse>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    suspend fun notify(response: ApiResponse) {
        responseFlow.emit(response)
    }

    suspend fun receive(id: UUID, timeout: Duration): Res<ApiResponse, ReceiveTimeoutErr> {
        val res = CoroutineScope(currentCoroutineContext()).async {
            responseFlow.filter { it.id == id }
                .first()
        }
        val cancelJob = CoroutineScope(currentCoroutineContext()).launch {
            delay(timeout)
            res.cancel()
        }
        res.join()
        cancelJob.cancel()

        if (res.isCancelled) return Res.Error(ReceiveTimeoutErr(id, timeout))
        return Res.Success(res.await())
    }
}
