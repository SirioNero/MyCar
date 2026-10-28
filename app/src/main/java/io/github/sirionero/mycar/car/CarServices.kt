package io.github.sirionero.mycar.car

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.host.CarAppCatalog
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.host.KnownApps
import io.github.sirionero.mycar.host.TemplateApp
import io.github.sirionero.mycar.media.CarMedia
import io.github.sirionero.mycar.media.MediaApp
import io.github.sirionero.mycar.media.MediaCatalog
import io.github.sirionero.mycar.notify.MyCarNotificationListener
import io.github.sirionero.mycar.xonos.Xonos
import io.github.sirionero.mycar.xonos.XonosAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The two long-lived things the car keeps running: the map app and the media app.
 *
 * They belong to the car session rather than to whichever screen is showing, so the home
 * screen, the Car apps list and the media screen all look at the same connection. Leaving a
 * screen must not drop the route being followed or the song being played.
 */
object CarServices {

    private var context: Context? = null

    val nav: CarAppConnection by lazy { CarAppConnection(requireNotNull(context)) }
    val media: CarMedia by lazy { CarMedia(requireNotNull(context)) }

    /** [nav], or null before the car screen has set things up. */
    fun navOrNull(): CarAppConnection? = if (context != null) nav else null

    /** [media], or null before the car screen has set things up: for code outside the car UI. */
    fun mediaOrNull(): CarMedia? = if (context != null) media else null

    /**
     * A second, independent connection for templated apps that aren't navigation apps - Spotify's
     * own Car App Library service is the first of these. Kept apart from [nav] so opening one
     * can never bump the map off Home: this one gets its own screen ([CarScreen.Browse]) instead.
     */
    val browse: CarAppConnection by lazy { CarAppConnection(requireNotNull(context)) }

    private val _mediaApp = MutableStateFlow<MediaApp?>(null)
    val mediaApp: StateFlow<MediaApp?> = _mediaApp.asStateFlow()

    /**
     * Whether the phone has any navigation app installed at all. True until [autostart] has
     * actually checked, so the home screen never flashes "none found" before it knows.
     */
    private val _hasNavApps = MutableStateFlow(true)
    val hasNavApps: StateFlow<Boolean> = _hasNavApps.asStateFlow()

    /**
     * True once the drive's first [autostart] has picked its music app: what's playing on the
     * phone, or the app used last. A music app beside the map waits for this before it opens.
     */
    private val _mediaDecided = MutableStateFlow(false)
    val mediaDecided: StateFlow<Boolean> = _mediaDecided.asStateFlow()

    /** Set when the driver stops the map by hand, so going Home doesn't immediately restart it. */
    private var navStopped = false

    /** Whether the map is idle because the driver stopped it, rather than not started yet. */
    val navStoppedByDriver: Boolean get() = navStopped
    private var autoplayPending = false

    /**
     * Who holds the drive's services: the car session itself and each car UI composed (the car's,
     * and the phone's preview of it). They go only when the last lets go: a car UI torn down for
     * screen sharing, or the preview closed mid-drive, used to end them all - the map unhooked,
     * Plancia and Xonos gone - in the middle of a drive.
     */
    private var holders = 0

    fun init(appContext: Context) {
        context = appContext.applicationContext
        if (holders++ > 0) return
        io.github.sirionero.mycar.notify.CarMotion.start(appContext)
        io.github.sirionero.mycar.call.CarCalls.start(appContext, inCar = true)
        CarAssistant.init(appContext)
        DirectionsVoice.init(appContext)
        CarBluetooth.init(appContext)
        BluetoothAudioCheck.init(appContext)
        LocationCheck.init(appContext)
        CarKeys.init(appContext)
        VoiceReply.init(appContext)
        // Plancia stays connected to Home Assistant through the drive, ready before it's opened.
        io.github.sirionero.mycar.home.HomeAssistant.acquire()
        // Xonos inside myCar takes the Xonos app's account if it has none yet; and while it's the
        // car's music, which it plays into the car by itself, Android isn't asked to share the audio.
        XonosAccount.adoptIfUnset(appContext)
        CarAudio.consentWaits = ::xonosIsTheMusic
        // Loaded now, while nothing's waiting on it, so the first reply isn't slower than the rest.
        io.github.sirionero.mycar.speech.SpeechEngine.warm(appContext)
    }

    // --- map -----------------------------------------------------------------------------

    /**
     * [destination] is a search query to start navigating to right away - "navigate to X with Y"
     * from the voice assistant - left null for a plain app launch. An app that's already the one
     * running gets the destination sent to its live session instead of being torn down and
     * rebuilt, same as [CarAppConnection.navigateTo] documents.
     */
    fun connectNav(app: TemplateApp, frame: CarEnvironment.Frame, destination: String? = null) {
        navStopped = false
        CarSettings.setLastNav(app.component.flattenToString())
        val same = nav.connectedComponent == app.component
        if (destination != null && same && nav.status.value.phase in LIVE) {
            nav.navigateTo(destination)
        } else if (destination == null && same && nav.status.value.phase in LIVE) {
            // Already there ("show me the map" to the assistant): connecting again would end the
            // route being driven.
            return
        } else {
            // Another map app taking over ends the old one's route, or it keeps guiding unseen.
            if (nav.connectedComponent != app.component) endRoute()
            nav.connect(app, frame.width, frame.height, frame.densityDpi, destination)
        }
    }

    private val LIVE = setOf(
        CarAppConnection.Phase.BINDING,
        CarAppConnection.Phase.HANDSHAKE,
        CarAppConnection.Phase.RUNNING,
    )

    fun stopNav() {
        navStopped = true
        endRoute()
        nav.disconnect()
    }

    private fun endRoute() {
        if (nav.navigating) nav.stopNavigating()
    }

    /** Undoes [stopNav]'s hold so the last map app comes back, same as it would after a restart. */
    suspend fun reconnectNav(frame: CarEnvironment.Frame) {
        navStopped = false
        // The map only: a music app the driver closed stays closed.
        autostart(frame, withMedia = false)
    }

    // --- browse (non-navigation templated apps) -------------------------------------------

    /** Opens a templated app that isn't navigation, on its own screen - never on Home. */
    fun connectBrowse(app: TemplateApp, frame: CarEnvironment.Frame) {
        closedBrowse = null
        // Already open ("open Plancia" to the assistant, with it beside the map): connecting again
        // threw away the screens the driver had opened in it.
        if (browse.connectedComponent == app.component && browse.status.value.phase in LIVE) return
        browse.connect(app, frame.width, frame.height, frame.densityDpi)
    }

    fun stopBrowse() {
        closedBrowse = browse.connectedComponent
        browse.disconnect()
    }

    /**
     * The templated app the driver closed, from its menu or its Stop button. The panes that
     * start their app by themselves (beside the map, on a page) leave this one closed until it's
     * asked for: coming back to Home used to start it straight up again.
     */
    var closedBrowse: ComponentName? = null
        private set

    // --- media ---------------------------------------------------------------------------

    fun openMedia(app: MediaApp, autoplay: Boolean = false) {
        CarSettings.setLastMedia(app.component.flattenToString())
        val phase = media.phase.value
        val live = phase == CarMedia.Phase.READY || phase == CarMedia.Phase.CONNECTING
        if (_mediaApp.value?.component == app.component && live) return

        // Xonos inside myCar, left for another music app: paused here. Without the audio focus
        // (refused in the background on Android 17) nothing else tells it, and straight into the
        // car's channel it kept the car's sound from the new app.
        val previous = _mediaApp.value
        if (previous != null && Xonos.isInside(previous.component) && media.now.value.playing) media.togglePlay()

        _mediaApp.value = app
        autoplayPending = autoplay
        media.connect(app)
        // Not torn down and rebuilt per app: the capture covers whatever the phone plays, so
        // switching media apps never costs the driver another consent dialog.
        if (CarSettings.pipeAudio.value) CarAudio.request()
    }

    /**
     * Reopens the media app used last, for the car's media button when nothing is open. False
     * when there's none on record or it's been uninstalled since.
     */
    suspend fun reopenLastMedia(): Boolean {
        val ctx = context ?: return false
        val last = CarSettings.lastMedia.value?.let(ComponentName::unflattenFromString) ?: return false
        val app = withContext(Dispatchers.IO) { MediaCatalog.installed(ctx) }
            .let { catalog -> catalog.firstOrNull { it.component == last } ?: xonosFor(last, catalog) } ?: return false
        openMedia(app)
        return true
    }

    /** Closes the media app from the car: stops what it's playing and drops the connection. */
    fun closeMedia() {
        if (media.now.value.playing) media.togglePlay()
        media.disconnect()
        _mediaApp.value = null
        autoplayPending = false
    }

    /** Called once the media app is connected: starts playback if that was asked for. */
    fun autoplayIfDue() {
        if (!autoplayPending) return
        autoplayPending = false
        if (!media.now.value.playing) media.togglePlay()
    }

    // --- start and stop ------------------------------------------------------------------

    /** Brings back whatever was in use last time. Safe to call repeatedly. */
    suspend fun autostart(frame: CarEnvironment.Frame, withMedia: Boolean = true) {
        val ctx = context ?: return

        if (!navStopped && nav.status.value.phase == CarAppConnection.Phase.IDLE) {
            val navApps = withContext(Dispatchers.IO) { CarAppCatalog.installed(ctx) }
                .filter { it.isNavigation }
            _hasNavApps.value = navApps.isNotEmpty()
            // Never auto-pick one already known to fail outright - a driver who plugs in should
            // land on a map, not on the one tile marked with a red X.
            val candidates = navApps.filterNot { KnownApps.isBroken(ctx, it.component.packageName) }
            val last = CarSettings.lastNav.value
            // The one used last, or - first run, nothing on record yet - whichever comes first:
            // a driver who plugs in for the first time shouldn't have to go pick one by hand
            // before the map shows up at all.
            val lastComponent = last?.let(ComponentName::unflattenFromString)
            val app = candidates.firstOrNull { it.component == lastComponent } ?: candidates.firstOrNull()
            if (app != null) connectNav(app, frame)
        }

        // Once per drive: a car UI made again (after screen sharing, a new encoder) used to reopen
        // the music app the driver had closed - and with Autoplay, start it.
        if (!withMedia || _mediaDecided.value) return
        if (_mediaApp.value == null) {
            val catalog = withContext(Dispatchers.IO) { MediaCatalog.installed(ctx) }
            // Something already playing on the phone wins over whatever was last used in the car -
            // a driver who started a song before plugging in shouldn't have to go open its tile by
            // hand just to see what's already going.
            val playing = withContext(Dispatchers.IO) { currentlyPlaying(ctx, catalog) }
            if (playing != null) {
                openMedia(playing)
            } else {
                val last = CarSettings.lastMedia.value?.let(ComponentName::unflattenFromString)
                val app = last?.let { component -> catalog.firstOrNull { it.component == component } ?: xonosFor(component, catalog) }
                if (app != null) openMedia(app, autoplay = CarSettings.autoplay.value)
            }
        }
        _mediaDecided.value = true
    }

    /**
     * The car's music is Xonos inside myCar: the one open, or, until one opens, the one used last -
     * or none yet, with Xonos the music app there is.
     */
    private fun xonosIsTheMusic(): Boolean {
        if (!Xonos.setUp.value) return false
        val app = _mediaApp.value?.component
            ?: CarSettings.lastMedia.value?.let(ComponentName::unflattenFromString)
            ?: return true
        return Xonos.isInside(app) || app.packageName == Xonos.APP_PACKAGE
    }

    /** Xonos inside myCar, in place of the Xonos app last used in the car, once it's set up. */
    private fun xonosFor(last: ComponentName, catalog: List<MediaApp>): MediaApp? =
        if (last.packageName == Xonos.APP_PACKAGE) catalog.firstOrNull { Xonos.isInside(it.component) } else null

    /**
     * Whichever known car media app has an actively playing session right now, system-wide - not
     * just the one myCar itself last connected to. Reading another app's session needs
     * notification access, the same permission [io.github.sirionero.mycar.media.CarMedia]
     * already asks for to control apps that won't share their library.
     */
    private fun currentlyPlaying(ctx: Context, catalog: List<MediaApp>): MediaApp? {
        val manager = ctx.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(ctx, MyCarNotificationListener::class.java)
        val sessions = try {
            manager.getActiveSessions(listener)
        } catch (e: SecurityException) {
            return null
        }
        val playingPackages = sessions
            .filter { it.playbackState?.state in SOUNDING }
            .map { it.packageName }
        return playingPackages.firstNotNullOfOrNull { pkg -> catalog.firstOrNull { it.component.packageName == pkg } }
    }

    /**
     * The car went away mid-song: pause it, as Android Auto does, so it doesn't carry on out of
     * the phone's speaker. Unplugging a USB accessory isn't "audio becoming noisy" the way
     * headphones are, so the apps won't pause themselves. Every playing session is paused, not
     * just the one myCar opened: anything the car was playing was going through the car.
     */
    /**
     * Playing, or about to: a stream filling its buffer (after a skip, in a tunnel) starts sounding a
     * moment later - out of the phone's speaker, once the car has gone.
     */
    private val SOUNDING = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)

    fun pausePhoneMedia(why: String = "the car left", keep: String? = null) {
        val ctx = context ?: return
        val manager = ctx.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(ctx, MyCarNotificationListener::class.java)
        val playing = try {
            manager.getActiveSessions(listener).filter { it.playbackState?.state in SOUNDING && it.packageName != keep }
        } catch (e: SecurityException) {
            null
        }
        if (playing == null) {
            // No notification access: the app myCar itself is driving is all it can reach.
            mediaOrNull()?.let { if (it.now.value.playing) it.togglePlay() }
            return
        }
        playing.forEach { runCatching { it.transportControls.pause() } }
        if (playing.isNotEmpty()) {
            io.github.sirionero.mycar.MyCarLog.i("media: paused ${playing.joinToString { it.packageName }}: $why")
        }
    }

    /** The car went away: nothing should keep running for a screen that no longer exists. */
    fun shutdown() {
        if (holders == 0 || --holders > 0) return
        context?.let { io.github.sirionero.mycar.notify.CarMotion.stop(it) }
        io.github.sirionero.mycar.call.CarCalls.stop()
        runCatching { nav.disconnect() }
        runCatching { media.disconnect() }
        runCatching { browse.disconnect() }
        CarAssistant.cancel()
        io.github.sirionero.mycar.home.HomeAssistant.release()
        // About 900 MB that nothing needs once the car's gone.
        io.github.sirionero.mycar.speech.SpeechEngine.unload()
        CarAudio.release()
        _mediaApp.value = null
        _mediaDecided.value = false
        closedBrowse = null
        clearSectionCache()
        navStopped = false
    }
}
