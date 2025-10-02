package net.kigawa.keruta.executor.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.kigawa.keruta.executor.domain.receive.ApiResponse
import kotlin.time.Duration

class ApiResponseNotifier {
    private val responseFlow = MutableSharedFlow<ApiResponse>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    suspend fun notify(response: ApiResponse) {
        responseFlow.emit(response)
    }

    suspend fun receive(timeout: Duration, block: (ApiResponse) -> Unit): Result<Unit, Unit> {
        val job = CoroutineScope(currentCoroutineContext()).launch {
            responseFlow.collect(block)
        }
        delay(timeout)
        job.cancelAndJoin()
        if (job.isCancelled) return Result.Failure(Unit)
        return Result.Success(Unit)
    }
}

