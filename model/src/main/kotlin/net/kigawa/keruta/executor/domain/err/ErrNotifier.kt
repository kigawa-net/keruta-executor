package net.kigawa.keruta.executor.domain.err

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class ErrNotifier {

    private val flow = MutableSharedFlow<KerutaErr>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    suspend fun notify(err: KerutaErr) {
        flow.emit(err)
    }

    suspend fun receive(block: suspend (KerutaErr) -> Unit) {
        flow.collect(block)
    }

    inline fun <T> handleToNull(block: () -> Res<T, out KerutaErr>): T? {
        when (val res = block()) {
            is Res.Error -> CoroutineScope(Dispatchers.Default).launch {
                notify(res.error)
            }

            is Res.Success -> return res.value
        }
        return null
    }
}
