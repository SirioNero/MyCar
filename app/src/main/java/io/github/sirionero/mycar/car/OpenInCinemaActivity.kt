package io.github.sirionero.mycar.car

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.SessionStatus
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.cinema.OpenWith
import io.github.sirionero.mycar.mirror.PhoneMirror

/**
 * "Open with" and "Share" from other apps, to Cinema on the car's screen ("myCar · Cinema" in Android's
 * list): NewPipe and its kin hand a video stream to an outside player this way, and any app can share a
 * link. Nothing of its own shows on the phone, only a short message: the car's screen turns to Cinema
 * (see CinemaAsks). A page, or a video at a home address, opens only once the driver says yes on the car's
 * screen (CinemaAskCard): any app can send one, and myCar may reach the home network where that app may
 * not. With no car connected it says so and keeps nothing: a video that started by itself on a later drive
 * would be a surprise. Only web addresses are taken (see OpenWith), to be played or shown, never to start
 * anything.
 */
class OpenInCinemaActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nothing to show (Theme.NoDisplay): it's done before it would be. Sent once, not again on a restore.
        // Whatever another app sends, myCar - and a drive under way - carries on.
        if (savedInstanceState == null) {
            runCatching { handle(intent) }.onFailure { MyCarLog.e("cinema: what another app sent couldn't be opened", it) }
        }
        finish()
    }

    private fun handle(intent: Intent) {
        // The app that sent it, by its package: a referrer of another kind can be a web page, kept out of the log.
        val from = referrer?.takeIf { it.scheme == "android-app" }?.host ?: "another app"
        // Extras from any app: ones that can't be read (a class only the sender has) are the same as none.
        val asked = runCatching {
            OpenWith.read(intent.action, intent.dataString, intent.type, intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(), titleIn(intent))
        }.getOrDefault(OpenWith.Unusable)
        val what = when (asked) {
            is OpenWith.Video -> (if (asked.stream) "a stream" else "a video") + logged(asked.url)
            is OpenWith.Page -> "a link" + logged(asked.url)
            OpenWith.Unusable -> {
                MyCarLog.i("cinema: nothing to open in what $from sent (${intent.action?.substringAfterLast('.')}, ${intent.type})")
                say(R.string.open_with_nothing, long = true)
                return
            }
        }
        val phase = SessionStatus.state.value.phase
        // The car's screen up, or about to be (made again for a new encoder, or after screen sharing), or a car
        // connecting: the car's screen opens it as soon as it's there.
        val now = phase == SessionStatus.Phase.PROJECTING || CinemaAsks.screenUp
        if (!now && phase != SessionStatus.Phase.CONNECTING) {
            MyCarLog.i("cinema: $what from $from, with no car connected - not kept")
            say(R.string.open_with_no_car, long = true)
            return
        }
        val ask = askFor(asked) ?: return
        CinemaAsks.send(ask)
        MyCarLog.i(
            "cinema: $what from $from - " + (if (ask.agreed) "opening in Cinema in the car" else "the car's screen asks the driver first") +
                if (now) "" else " once it's connected",
        )
        // The phone's screen shared to the car: no car screen to open it on. The sharing ends, as for the
        // assistant, and the car's screen, back in a moment, opens it.
        if (PhoneMirror.active.value) {
            MyCarLog.i("cinema: the phone's screen was shared - back to myCar's screen")
            PhoneMirror.requestStop()
        }
        when {
            !ask.agreed -> say(R.string.open_with_ask_in_car, long = true)
            now -> say(R.string.open_with_opening, long = false)
            else -> say(R.string.open_with_soon, long = true)
        }
    }

    /** What the car's screen opens for [asked]; null for nothing it can. */
    private fun askFor(asked: OpenWith.Asked): CinemaAsks.Ask? {
        val at = SystemClock.elapsedRealtime()
        return when (asked) {
            is OpenWith.Video -> CinemaAsks.Video(
                CinemaMedia(
                    title = asked.title ?: Words.get(R.string.open_with_untitled),
                    uri = Uri.parse(asked.url),
                    // A video starts again where it was left, as the phone's do: known by its title, which stays when
                    // the app sends it again (its stream's address doesn't). A stream may be live: no place in it.
                    resumeKey = if (asked.stream) null else "open:" + (asked.title ?: asked.url),
                ),
                at,
                // A stream's server on the internet plays at once, as with NewPipe; one at home waits for a yes.
                agreed = !atHome(asked.url),
            )
            is OpenWith.Page -> CinemaAsks.Page(asked.url, at)
            OpenWith.Unusable -> null
        }
    }

    /**
     * Whether a video at [url] may be on the home network - a private, link-local or .local address, or one that
     * may lead there (OpenWith.oddHost) - which myCar may reach where the app that sent it may not: then the
     * driver says yes first, as for a page. A name the home router answers with a private address can't be told
     * from here (see LocalNetwork.isLocal).
     */
    private fun atHome(url: String): Boolean {
        val host = OpenWith.hostOf(url) ?: return true
        return OpenWith.oddHost(host) || LocalNetwork.isLocal("http://$host")
    }

    /** The name the app gave it: NewPipe's in EXTRA_TITLE, others' in "title", a shared page's in EXTRA_SUBJECT. */
    private fun titleIn(intent: Intent): String? =
        listOf(Intent.EXTRA_TITLE, "title", Intent.EXTRA_SUBJECT).firstNotNullOfOrNull { key ->
            intent.getCharSequenceExtra(key)?.toString()?.takeIf { it.isNotBlank() }
        }

    /** The address, in the debug build only, as Cinema's player names a video: the log is shared for help. */
    private fun logged(address: String): String = if (BuildConfig.DEBUG) " ($address)" else ""

    private fun say(@StringRes text: Int, long: Boolean) {
        Toast.makeText(applicationContext, Words.get(text), if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
