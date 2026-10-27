package io.github.sirionero.mycar.xonos.playback

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Quello che fa `future {}` di kotlinx-coroutines-guava, per le poche risposte che Media3 vuole
 * come ListenableFuture: in myCar senza una libreria in più. Se Media3 annulla, si ferma anche il lavoro.
 */
internal fun <T> CoroutineScope.future(block: suspend CoroutineScope.() -> T): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    val job = launch {
        try {
            result.set(block())
        } catch (e: CancellationException) {
            result.cancel(false)
            throw e
        } catch (e: Throwable) {
            result.setException(e)
        }
    }
    result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
    return result
}
