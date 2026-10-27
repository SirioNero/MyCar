package io.github.sirionero.mycar.xonos.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manda i salti (avanti, indietro, un brano della coda) a Music Assistant uno alla volta, sempre
 * verso l'ultimo brano chiesto. Due comandi insieme Music Assistant li esegue in parallelo, e
 * l'ultimo a finire vince: può essere il primo, e la coda resta sul brano sbagliato.
 *
 * Con un servizio che dà un flusso alla volta (vedi [QueueItem.singleStream]) si aspetta anche che
 * i tocchi si fermino per [GATHER_MS]: ogni salto fa partire un flusso, e una raffica finirebbe in
 * "limite di flussi raggiunto". Con gli altri si salta subito.
 */
class SkipSender(
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val send: suspend (index: Int) -> Unit,
) {
    private val lock = Any()
    private var target: Int? = null
    private var gather = false
    private var lastAt = 0L
    private var running = false
    private val waiting = mutableListOf<() -> Unit>()

    /** Salta al brano [index] della coda; [done] quando il comando è arrivato (o è confluito in uno dopo). */
    fun request(index: Int, gather: Boolean, done: () -> Unit = {}) {
        synchronized(lock) {
            target = index
            this.gather = gather
            lastAt = clock()
            waiting += done
            if (running) return
            running = true
        }
        scope.launch { pump() }
    }

    private suspend fun pump() {
        while (true) {
            var wait = 0L
            var index: Int? = null
            var done: List<() -> Unit> = emptyList()
            synchronized(lock) {
                wait = if (gather) lastAt + GATHER_MS - clock() else 0L
                if (wait <= 0) {
                    index = target
                    target = null
                    done = waiting.toList()
                    waiting.clear()
                    if (index == null) running = false
                }
            }
            if (wait > 0) {
                delay(wait)
                continue
            }
            val next = index ?: return
            try {
                // Mandato, il comando va finito: solo dopo può partire il prossimo.
                withContext(NonCancellable) { send(next) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Lo stato vero torna con gli eventi della coda.
            }
            done.forEach { it() }
        }
    }

    companion object {
        /** Quanto si aspetta un altro tocco prima di saltare, con i servizi a un flusso alla volta. */
        const val GATHER_MS = 400L
    }
}
