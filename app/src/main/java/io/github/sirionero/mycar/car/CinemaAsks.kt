package io.github.sirionero.mycar.car

import android.os.SystemClock
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.cinema.OpenWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What another app sent to Cinema ("Open with", "Share": see OpenInCinemaActivity) until the car's screen
 * opens it: the car's screen turns to Cinema ([watch]), and Cinema plays the video or opens the page
 * ([take]). What could reach the home network - a page, or a video at a home address - waits for the
 * driver's yes on the car's screen first ([CinemaAskCard]). Kept a little while - for a car still
 * connecting, a car's screen being made again, a card not yet answered - and after that it's old news:
 * dropped, so it never pops up on a later drive. One at a time: a new one takes the place of the last.
 */
internal object CinemaAsks {

    sealed interface Ask {
        /** When it was sent, on elapsedRealtime. */
        val at: Long

        /**
         * Whether it may open. A page's scripts would run in myCar's web view, which may reach the home
         * network where the app that sent it may not; a video's address at home would be asked for by
         * myCar: those wait for the driver's yes ([agree]). Any other video needs none.
         */
        val agreed: Boolean
    }

    /** A video for Cinema's player. */
    data class Video(val media: CinemaMedia, override val at: Long, override val agreed: Boolean = true) : Ask

    /** A link for Cinema's browser: on its site, if that's one of Cinema's, on a site of its own if not. */
    data class Page(val url: String, override val at: Long, override val agreed: Boolean = false) : Ask

    private val flow = MutableStateFlow<Ask?>(null)
    val asked: StateFlow<Ask?> = flow.asStateFlow()

    /** The car's screens up now, which open what's sent at once ([watch]): on the main thread only. */
    @Volatile private var screens = 0

    /** Whether a car's screen is up to open what's sent now. */
    val screenUp: Boolean get() = screens > 0

    fun send(ask: Ask) {
        flow.value = ask
    }

    /** The driver's yes on the car's screen: [ask] opens. */
    fun agree(ask: Ask) {
        val yes = when (ask) {
            is Video -> ask.copy(agreed = true)
            is Page -> ask.copy(agreed = true)
        }
        if (flow.compareAndSet(ask, yes)) MyCarLog.i("cinema: the driver opened what another app sent")
    }

    /** The driver's no ("Not now", Back, a tap beside the card), or no answer in time: [ask] goes. */
    fun dismiss(ask: Ask) {
        if (flow.compareAndSet(ask, null)) MyCarLog.i("cinema: what another app sent wasn't opened")
    }

    /** [ask], taken so nothing opens it twice; null if another took it, or if it waited too long. */
    fun take(ask: Ask): Ask? {
        if (!flow.compareAndSet(ask, null)) return null
        if (fresh(ask)) return ask
        MyCarLog.i("cinema: what another app sent waited too long - not opened")
        return null
    }

    /** On a car's screen, for as long as it's up: turns it to Cinema for what's sent, once it may open. */
    suspend fun watch(navigator: CarNavigator) {
        screens++
        try {
            asked.collect { ask ->
                if (ask == null) return@collect
                if (!fresh(ask)) {
                    take(ask)
                    return@collect
                }
                // Its card asks the driver first, over whatever is on the screen.
                if (!ask.agreed) return@collect
                // Cinema further back on the way (under the settings, say): back to it, not a second one.
                val at = navigator.trail.indexOfLast { it.screen == CINEMA }
                if (at >= 0) navigator.jumpTo(at) else navigator.open(CINEMA.id)
            }
        } finally {
            screens--
        }
    }

    /** Whether [ask] is still news. */
    fun fresh(ask: Ask): Boolean = timeLeft(ask) > 0

    /** How long [ask] stays news, in milliseconds. */
    fun timeLeft(ask: Ask): Long = (ask.at + KEPT_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    /** Where [ask] leads, for its card: host and port, as the driver can check them. */
    fun placeOf(ask: Ask): String {
        val url = when (ask) {
            is Video -> ask.media.uri.toString()
            is Page -> ask.url
        }
        return OpenWith.serverOf(url) ?: url
    }

    private val CINEMA = CarScreen.App("cinema")

    /**
     * How long what's sent waits for the car's screen (a wireless car takes up to a minute to connect), and
     * a card for the driver's answer.
     */
    private const val KEPT_MS = 2 * 60_000L
}
