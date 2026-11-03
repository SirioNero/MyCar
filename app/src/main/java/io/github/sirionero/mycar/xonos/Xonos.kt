package io.github.sirionero.mycar.xonos

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.bridge.BridgeDns
import io.github.sirionero.mycar.bridge.BridgeSocketFactory
import io.github.sirionero.mycar.bridge.Bridges
import io.github.sirionero.mycar.media.MediaApp
import io.github.sirionero.mycar.xonos.data.ArtCache
import io.github.sirionero.mycar.xonos.data.Connection
import io.github.sirionero.mycar.xonos.data.Library
import io.github.sirionero.mycar.xonos.data.Logged
import io.github.sirionero.mycar.xonos.data.MaRepository
import io.github.sirionero.mycar.xonos.data.Problem
import io.github.sirionero.mycar.xonos.data.Settings
import io.github.sirionero.mycar.xonos.playback.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI

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

    init {
        // Before anything else: in the engine's logcat the details (titles, names, addresses, error texts) only in
        // the debug build, as myCar's rule wants (see data/Logged).
        Logged.details = BuildConfig.DEBUG
    }

    private lateinit var app: Context

    /** Idempotent; any context will do. */
    fun init(context: Context) {
        if (!::app.isInitialized) {
            app = context.applicationContext
            readSwitch()
        }
        Bridges.init(app)
    }

    private val enabledFlow = MutableStateFlow(false)

    /**
     * Xonos on in the car (the phone's settings, Music): only then is it anywhere on the car's
     * screen - Xonos inside myCar and the Xonos app alike. Off to start with; on, the first time this
     * is read, for a phone already using Xonos (an account in myCar, or the Xonos app on the phone),
     * so an update doesn't take it from the car.
     */
    val enabled: StateFlow<Boolean> = enabledFlow.asStateFlow()

    fun setEnabled(context: Context, on: Boolean) {
        init(context)
        enabledFlow.value = on
        AppSettings.putString(app, KEY_ON, on.toString())
    }

    private fun readSwitch() {
        val saved = AppSettings.getString(app, KEY_ON, "")
        enabledFlow.value = if (saved.isNotEmpty()) {
            saved == "true"
        } else {
            val inUse = settings.load().isComplete || runCatching { app.packageManager.getPackageInfo(APP_PACKAGE, 0) }.isSuccess
            AppSettings.putString(app, KEY_ON, inUse.toString())
            inUse
        }
    }

    val settings: Settings by lazy { Settings(app) }

    /** Left out of Tailscale to connect wirelessly, myCar reaches a Tailscale address through the Xonos app (see bridge/Bridges). */
    val repo: MaRepository by lazy {
        MaRepository(settings) {
            dns(BridgeDns(Bridges.xonos))
            socketFactory(BridgeSocketFactory(Bridges.xonos))
        }
    }
    val library: Library by lazy { Library(repo).also { it.playTimeoutMs = playTimeout() } }

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
        library.playTimeoutMs = playTimeout()
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

    /**
     * Whether the account's Music Assistant is the one inside the Xonos app on this phone (127.0.0.1:8095): myCar
     * reaches it through the bridge, and the Xonos app stops it when nobody uses it and starts it again when the
     * bridge is opened (see bridge/Bridges.onThePhone).
     */
    val onPhoneServer: Boolean
        get() = runCatching { URI(settings.load().homeUrl).host }.getOrNull()?.let(Bridges::onThePhone) == true

    /**
     * How long play_media is waited for (see Library.playTimeoutMs): longer with the Xonos app's Music Assistant,
     * where Spotify's requests keep to its limit and the first play of a long playlist or an artist takes longer.
     */
    private fun playTimeout() = if (onPhoneServer) Library.PHONE_PLAY_TIMEOUT_MS else Library.PLAY_TIMEOUT_MS

    /**
     * The Music Assistant inside the Xonos app, stopped because nobody used it (or closed by Android), starts again
     * when myCar opens the bridge, and stays on while myCar keeps it bound: a play, a skip, a choice or the car's
     * menu waits for it (see MaPlayer and PlaybackService) rather than failing at once. True if it's worth waiting:
     * the account's server is that one, the Xonos app is there with its bridge, and the server hasn't turned myCar
     * down for good. The connection then tries again now, not at its next attempt - which after a few failures
     * comes up to 20 s later.
     */
    fun wakePhoneServer(): Boolean {
        if (!onPhoneServer || !Bridges.xonos.available(app) || phoneServerRefused()) return false
        val now = SystemClock.elapsedRealtime()
        if (repo.connection.value is Connection.Failed && now - wokenAt > WAKE_AGAIN_MS) {
            wokenAt = now
            repo.reconnect()
        }
        return true
    }

    @Volatile private var wokenAt = 0L

    /**
     * The Xonos app's Music Assistant answered and turned myCar down for a reason that won't pass by itself (the
     * token, the version...), or the Xonos app itself did (its server isn't this address any more: it moved to another
     * Music Assistant or took its own off the phone, and won't start it for myCar): no use waiting for it to start (see
     * MaPlayer). How the server's own process is doing the Xonos app doesn't tell myCar: these are the sure signs.
     */
    fun phoneServerRefused(): Boolean =
        (repo.connection.value as? Connection.Failed)?.problem in REFUSED_FOR_GOOD ||
            runCatching { URI(settings.load().homeUrl).host }.getOrNull()?.let(Bridges.xonos::refusedForGood) == true

    private val REFUSED_FOR_GOOD = setOf(
        Problem.BAD_TOKEN,
        Problem.TOO_OLD,
        Problem.DENIED,
        Problem.NOT_MUSIC_ASSISTANT,
        Problem.SETUP_REQUIRED,
        Problem.BAD_ADDRESS,
    )

    /** The player, as the car's media screens connect to it. */
    fun component(context: Context) = ComponentName(context, PlaybackService::class.java)

    /** Whether [component] is Xonos inside myCar rather than an installed app. */
    fun isInside(component: ComponentName) = component.className == PlaybackService::class.java.name

    /** Xonos as one of the car's music apps, once it's set up and on; null otherwise, and while the app takes over. */
    fun mediaApp(context: Context): MediaApp? {
        refresh(context)
        return if (enabledFlow.value && _setUp.value && !appTakesOver.value) MediaApp(component(context), LABEL, Process.myUid()) else null
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
    private const val KEY_ON = "xonos_in_the_car"

    /** How soon a wake ([wakePhoneServer]) may make the connection try again: several asks at once make one try. */
    private const val WAKE_AGAIN_MS = 2_000L
}
