package io.github.sirionero.mycar.car

import android.os.SystemClock
import io.github.sirionero.mycar.MyCarLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What another app sent to Cinema ("Open with", "Share": see OpenInCinemaActivity) until the car's screen
 * opens it: the car's screen turns to Cinema ([watch]), and Cinema plays the video or opens the page
 * ([take]). Kept a little while - for a car still connecting, or a car's screen being made again - and after
 * that it's old news: dropped, so it never pops up on a later drive.
 */
internal object CinemaAsks {

    sealed interface Ask {
        /** When it was sent, on elapsedRealtime. */
        val at: Long
    }

    /** A video for Cinema's player. */
    data class Video(val media: CinemaMedia, override val at: Long) : Ask

    /** A link for Cinema's browser: on its site, if that's one of Cinema's, on a site of its own if not. */
    data class Page(val url: String, override val at: Long) : Ask

    private val flow = MutableStateFlow<Ask?>(null)
    val asked: StateFlow<Ask?> = flow.asStateFlow()

    /** The car's screens up now, which open what's sent at once ([watch]): on the main thread only. */
    @Volatile private var screens = 0

    /** Whether a car's screen is up to open what's sent now. */
    val screenUp: Boolean get() = screens > 0

    fun send(ask: Ask) {
        flow.value = ask
    }

    /** [ask], taken so nothing opens it twice; null if another took it, or if it waited too long. */
    fun take(ask: Ask): Ask? {
        if (!flow.compareAndSet(ask, null)) return null
        if (fresh(ask)) return ask
        MyCarLog.i("cinema: what another app sent waited too long - not opened")
        return null
    }

    /** On a car's screen, for as long as it's up: turns it to Cinema for what's sent. */
    suspend fun watch(navigator: CarNavigator) {
        screens++
        try {
            asked.collect { ask ->
                if (ask == null) return@collect
                if (!fresh(ask)) {
                    take(ask)
                    return@collect
                }
                // Cinema further back on the way (under the settings, say): back to it, not a second one.
                val at = navigator.trail.indexOfLast { it.screen == CINEMA }
                if (at >= 0) navigator.jumpTo(at) else navigator.open(CINEMA.id)
            }
        } finally {
            screens--
        }
    }

    private fun fresh(ask: Ask): Boolean = SystemClock.elapsedRealtime() - ask.at < KEPT_MS

    private val CINEMA = CarScreen.App("cinema")

    /** How long what's sent waits for the car's screen: a wireless car takes up to a minute to connect. */
    private const val KEPT_MS = 2 * 60_000L
}
