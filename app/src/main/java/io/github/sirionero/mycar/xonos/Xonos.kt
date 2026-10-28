package io.github.sirionero.mycar.xonos

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.core.content.ContextCompat
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.bridge.BridgeDns
import io.github.sirionero.mycar.bridge.BridgeSocketFactory
import io.github.sirionero.mycar.bridge.Bridges
import io.github.sirionero.mycar.media.MediaApp
import io.github.sirionero.mycar.xonos.data.ArtCache
import io.github.sirionero.mycar.xonos.data.Library
import io.github.sirionero.mycar.xonos.data.MaRepository
import io.github.sirionero.mycar.xonos.data.Settings
import io.github.sirionero.mycar.xonos.playback.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Xonos inside myCar: Music Assistant's player, set up on the phone and played in the car as one
 * of the car's music apps. One of each for the whole app, as the Xonos app keeps them in its
 * Application: the account, the connection to Music Assistant, the library and the covers.
 *
 * Its player is [PlaybackService], found by the car's media screens like any music app's, and
 * named "myCar" in Music Assistant. With the car audio through myCar, its sound goes straight
 * down the car's audio link (see CarLinkOutput) rather than through the phone's speakers.
 */
object Xonos {

    private lateinit var app: Context

    /** Idempotent; any context will do. */
    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
        Bridges.init(app)
    }

    val settings: Settings by lazy { Settings(app) }

    /** Left out of Tailscale to connect wirelessly, myCar reaches a Tailscale address through the Xonos app (see bridge/Bridges). */
    val repo: MaRepository by lazy {
        MaRepository(settings) {
            dns(BridgeDns(Bridges.xonos))
            socketFactory(BridgeSocketFactory(Bridges.xonos))
        }
    }
    val library: Library by lazy { Library(repo) }

    /** The covers fetched for the car, the notification and the player (see playback/Artwork). */
    val artCache: ArtCache by lazy { ArtCache(File(app.cacheDir, "xonos-art"), repo.http) }

    /** True once the player is Music Assistant's: its API and its audio both connected. */
    val playerReady = MutableStateFlow(false)

    /**
     * The Xonos app plays in the car instead, for the rest of the drive: this one can't reach Music
     * Assistant - myCar left out of Tailscale to connect wirelessly, a wrong address or token - and
     * the app, on the phone, can (see car/InsideOrApp). Xonos is never missing from the car's music.
     */
    val appTakesOver = MutableStateFlow(false)

    private val _setUp = MutableStateFlow(false)

    /** Whether there's an account to play with: only then is Xonos among the car's music apps. */
    val setUp: StateFlow<Boolean> = _setUp.asStateFlow()

    /** Reads the account once, so the car's app lists know whether Xonos is set up. */
    fun refresh(context: Context) {
        init(context)
        _setUp.value = settings.load().isComplete
    }

    /** Keeps an account (or an empty one, to remove it) and reconnects with it. */
    fun save(context: Context, config: Settings.Config, username: String?) {
        init(context)
        if (config.isComplete) AppSettings.putString(context, KEY_IMPORT_OFF, "")
        settings.save(config)
        settings.username = username
        _setUp.value = config.isComplete
        repo.reconnect()
    }

    /**
     * "Remove" on the phone: Xonos off in myCar, and the Xonos app's account isn't taken over again
     * by itself - otherwise the app, back in the car, was gone again at the next drive.
     */
    fun remove(context: Context) {
        AppSettings.putString(context, KEY_IMPORT_OFF, "1")
        save(context, Settings.Config("", "", ""), null)
    }

    /** Whether "Remove" was the last word: no account taken from the Xonos app by itself. */
    fun importOff(context: Context): Boolean = AppSettings.getString(context, KEY_IMPORT_OFF, "") == "1"

    /** The player, as the car's media screens connect to it. */
    fun component(context: Context) = ComponentName(context, PlaybackService::class.java)

    /** Whether [component] is Xonos inside myCar rather than an installed app. */
    fun isInside(component: ComponentName) = component.className == PlaybackService::class.java.name

    /** Xonos as one of the car's music apps, once it's set up; null until then, and while the app takes over. */
    fun mediaApp(context: Context): MediaApp? {
        refresh(context)
        return if (_setUp.value && !appTakesOver.value) MediaApp(component(context), LABEL, Process.myUid()) else null
    }

    /** Xonos's icon as an adaptive one, so it's framed exactly as the Xonos app's was. */
    fun icon(context: Context): Drawable? {
        val art = ContextCompat.getDrawable(context, R.drawable.ic_xonos) ?: return null
        return AdaptiveIconDrawable(ColorDrawable(ICON_BACKGROUND), art)
    }

    /** The Xonos app, whose place Xonos inside myCar takes in the car once it's set up. */
    const val APP_PACKAGE = "it.xonos.ma"

    const val LABEL = "Xonos"

    private const val ICON_BACKGROUND = 0xFF0E1420.toInt()
    private const val KEY_IMPORT_OFF = "xonos_import_off"
}
