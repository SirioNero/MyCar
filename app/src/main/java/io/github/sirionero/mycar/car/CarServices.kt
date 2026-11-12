package io.github.sirionero.mycar.car

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.audio.CarSource
import io.github.sirionero.mycar.audio.PausedForCar
import io.github.sirionero.mycar.host.CarAppCatalog
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.host.KnownApps
import io.github.sirionero.mycar.host.TemplateApp
import io.github.sirionero.mycar.media.CarMedia
import io.github.sirionero.mycar.media.MediaApp
import io.github.sirionero.mycar.media.MediaCatalog
import io.github.sirionero.mycar.webmusic.WebMusic
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
     * No music app to talk to: none open yet, one closed from its menu, or one that wouldn't
     * connect. One still connecting counts as there.
     */
    fun noMusicApp(): Boolean {
        val phase = mediaOrNull()?.phase?.value ?: return true
        return phase == CarMedia.Phase.IDLE || phase == CarMedia.Phase.REJECTED
    }

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

    /**
     * The drive's own music still to say its song - a web player or Xonos inside myCar once its page or
     * server is up, an app told to play: Home keeps the player's column for it from the start, so the
     * map's app is lent its final width at once. Each width is a new surface, and Vela builds its map
     * again (2 Nov 2026: 720 px at 13:03:45, 437 px 6 s later when Spotify Web said its song). Until its
     * first song ([musicCame]), [MUSIC_START_MS], or the music closed.
     */
    private val _musicStarting = MutableStateFlow(false)
    val musicStarting: StateFlow<Boolean> = _musicStarting.asStateFlow()
    private val musicWait = android.os.Handler(android.os.Looper.getMainLooper())
    private val musicWaitOver = Runnable { _musicStarting.value = false }

    private fun expectMusic(on: Boolean) {
        musicWait.removeCallbacks(musicWaitOver)
        _musicStarting.value = on
        if (on) musicWait.postDelayed(musicWaitOver, MUSIC_START_MS)
    }

    /** The music said its song: the column is the music's now. */
    fun musicCame() {
        if (_musicStarting.value) expectMusic(false)
    }

    /** Spotify Web said its song 6.4 s into the drive; Xonos through the bridge connects in up to ~15 s. */
    private const val MUSIC_START_MS = 20_000L

    /** Set when the driver stops the map by hand, so going Home doesn't immediately restart it. */
    private var navStopped = false

    /** Whether the map is idle because the driver stopped it, rather than not started yet. */
    val navStoppedByDriver: Boolean get() = navStopped
    private var autoplayPending = false
    /** The driver's asks of the music when Autoplay was set ([CarMedia.askMark]): anything since, and it's left alone. */
    private var autoplayAsks = 0
    /** When Autoplay began waiting for a Bluetooth output (elapsedRealtime), 0 if it isn't. */
    private var autoplayBluetoothSince = 0L

    /**
     * Autoplay set by the drive's own pick: it waits for the sound's way to the car (autoplayIfDue).
     * Its own, not [mediaAutoPicked]: the full-screen player opened on the same app clears that one,
     * and a change of the sound's way from there then played on the phone's speaker.
     */
    private var autoplayAutoPicked = false

    /** Autoplay, from now on, unless the driver asks the music for anything first. */
    private fun armAutoplay(on: Boolean, auto: Boolean) {
        autoplayPending = on
        autoplayAutoPicked = auto
        // Marked at the first autoplayIfDue, once the app is connected (see there).
        autoplayAsks = -1
        autoplayBluetoothSince = 0L
    }

    /**
     * The phone's preview of the car's screen is open (Settings, no car): its car UI picks the music
     * as a drive would, but plays nothing by itself - with Autoplay on, opening the preview started
     * the music on the phone's speaker - and pauses no app on the phone for a web player.
     */
    @Volatile var phonePreview = false

    /**
     * Who holds the drive's services: the car session itself and each car UI composed (the car's,
     * and the phone's preview of it). They go only when the last lets go: a car UI torn down for
     * screen sharing, or the preview closed mid-drive, used to end them all - the map unhooked,
     * Plancia and Xonos gone - in the middle of a drive.
     */
    private var holders = 0

    /** Whether this drive keeps Xonos connected to Music Assistant: Xonos was on when it started. */
    private var xonosHeld = false

    fun init(appContext: Context) {
        context = appContext.applicationContext
        if (holders++ > 0) return
        // Toasts posted between drives (after the last one ended) aren't this drive's.
        CarToasts.clearAll()
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
        // Xonos inside myCar, when it's on in the car, takes the Xonos app's account if it has none
        // yet; and while it's the car's music, which it plays into the car by itself, Android isn't
        // asked to share the audio.
        Xonos.init(appContext)
        if (Xonos.enabled.value) XonosAccount.adoptIfUnset(appContext)
        CarAudio.consentWaits = ::xonosIsTheMusic
        // Only a player's sound takes the car's speakers from its radio, not a map's voice or a page.
        CarAudio.playerPlaying = ::aPlayerPlays
        // Connected through the drive too, like Plancia: ready when it's opened, and known to reach
        // Music Assistant or not before the drive's music is picked.
        xonosHeld = Xonos.enabled.value
        if (xonosHeld) Xonos.repo.acquire()
        // The Plancia and Xonos apps in place of the ones inside myCar, if those can't connect.
        InsideOrApp.start(appContext)
        // Loaded ahead of the first command, so the first reply isn't slower than the rest - but not
        // while the car's first picture is being made: hundreds of MB of voice models read and set
        // up took the phone's cores and storage at the very moment the car connected. Not at all when
        // the phone's own speech recognition does the listening.
        speechWarm.removeCallbacks(warmSpeech)
        speechWarm.removeCallbacks(warmFinal)
        // Which models there are, at once: a command before the loading finds them, as before.
        io.github.sirionero.mycar.speech.SpeechModels.init(appContext)
        val phoneListens = CarSettings.systemSpeech.value && android.speech.SpeechRecognizer.isRecognitionAvailable(appContext)
        if (!phoneListens) {
            speechWarm.postDelayed(warmSpeech, SPEECH_WARM_AFTER_MS)
            speechWarm.postDelayed(warmFinal, FINAL_WARM_AFTER_MS)
        }
    }

    private val speechWarm = android.os.Handler(android.os.Looper.getMainLooper())
    private val warmSpeech = Runnable { warmModels(final = false) }

    /**
     * The model that rereads the phrase (Parakeet: ~950 MB and seconds of every core) only once the
     * drive has lasted a while: after many drives the car, switched off, made a session of 20-40 s
     * that loaded it for nobody - 19 loads for 2 commands in three days. A command before then loads
     * it at the press, if there's the memory for it (SpeechEngine).
     */
    private val warmFinal = Runnable { warmModels(final = true) }

    private fun warmModels(final: Boolean) {
        val app = context ?: return
        // The phone's preview of the car's screen alone (Settings, no car): no voice models for it.
        if (phonePreview && !io.github.sirionero.mycar.SessionService.running) return
        io.github.sirionero.mycar.speech.SpeechEngine.warm(
            app,
            io.github.sirionero.mycar.speech.VoiceLanguages.effective(
                app, CarSettings.systemSpeech.value && android.speech.SpeechRecognizer.isRecognitionAvailable(app),
            ),
            final,
        )
    }

    /** How long after the car connects the live voice model (Kroko) starts loading. */
    private const val SPEECH_WARM_AFTER_MS = 20_000L

    /** How long a drive lasts before the model that rereads the phrase loads ahead of a command. */
    private const val FINAL_WARM_AFTER_MS = 120_000L

    /**
     * Xonos switched on or off on the phone in the middle of a drive: connected to Music Assistant
     * now, as at a drive's start - not connected, it'd soon be taken for one that can't, and the
     * Xonos app put in its place - or let go, and out of the car's music if it was. On the main thread.
     */
    fun xonosSwitched(on: Boolean) {
        val appContext = context ?: return
        if (holders == 0 || on == xonosHeld) return
        xonosHeld = on
        if (on) {
            XonosAccount.adoptIfUnset(appContext)
            Xonos.repo.acquire()
        } else {
            // Inside myCar or the Xonos app: off, it's in the car no more, as the switch says.
            val music = _mediaApp.value?.component
            if (music != null && (Xonos.isInside(music) || music.packageName == Xonos.APP_PACKAGE)) closeMedia()
            Xonos.repo.release()
        }
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
        closedBrowse = closedBrowse - app.component
        // Already open ("open Plancia" to the assistant, with it beside the map): connecting again
        // threw away the screens the driver had opened in it.
        if (browse.connectedComponent == app.component && browse.status.value.phase in LIVE) return
        browse.connect(app, frame.width, frame.height, frame.densityDpi)
    }

    fun stopBrowse() {
        browse.connectedComponent?.let { closedBrowse = closedBrowse + it }
        browse.disconnect()
    }

    /**
     * The templated app the driver closed, from its menu or its Stop button. The panes that
     * start their app by themselves (beside the map, on a page) leave this one closed until it's
     * asked for: coming back to Home used to start it straight up again.
     */
    var closedBrowse: Set<ComponentName> = emptySet()
        private set

    // --- media ---------------------------------------------------------------------------

    /**
     * The music app open now is the drive's own pick ([autostart]), not one the driver chose: the
     * app beside the map may take its place if it doesn't connect.
     */
    var mediaAutoPicked = false
        private set

    /**
     * [remember]: false when nobody chose it - the app beside the map, opened to show its player -
     * so the next drive still starts with the app the driver last used. [auto]: the drive's pick.
     */
    fun openMedia(app: MediaApp, autoplay: Boolean = false, remember: Boolean = true, auto: Boolean = false) {
        if (remember) CarSettings.setLastMedia(app.component.flattenToString())
        closedMedia = null
        mediaAutoPicked = auto
        val phase = media.phase.value
        val live = phase == CarMedia.Phase.READY || phase == CarMedia.Phase.CONNECTING
        if (_mediaApp.value?.component == app.component && live) {
            // Already open, and asked to play: it plays, now or once it's connected.
            if (autoplay) {
                armAutoplay(true, auto)
                if (phase == CarMedia.Phase.READY) autoplayIfDue()
            }
            return
        }

        // myCar's own players - Xonos and the music services' web players inside myCar - left for
        // another music app: paused here. Without the audio focus (refused in the background on
        // Android 17) nothing else tells Xonos, and straight into the car's channel it kept the car's
        // sound from the new app; a web player in myCar's own process might play on too. The same
        // when one of them is picked: Xonos straight into the car's channel takes no focus from the
        // app before, which played on under it and came back in the car when Xonos stopped.
        val previous = _mediaApp.value
        val inside = { c: ComponentName -> Xonos.isInside(c) || WebMusic.isInside(c) }
        if (previous != null && (inside(previous.component) || inside(app.component)) && media.now.value.playing) {
            media.togglePlay()
        }

        _mediaApp.value = app
        // The drive's first pick, before its song: myCar's own players say it only once their page or
        // server is up, Autoplay or not; a phone app at once or never, so only when it's told to play.
        if (auto && !_mediaDecided.value && (autoplay || inside(app.component))) expectMusic(true)
        armAutoplay(autoplay, auto)
        media.connect(app)
        // Not torn down and rebuilt per app: the capture covers whatever the phone plays, so
        // switching media apps never costs the driver another consent dialog.
        if (CarSettings.pipeAudio.value) {
            // Picked by the driver: a "no" to Android's question earlier in the drive is asked once
            // more, as this music reaches the car only through the capture. Not the drive's own pick.
            if (!auto) CarAudio.askAgainOnce()
            CarAudio.request()
        }
    }

    /**
     * Reopens the media app used last, for the car's media button when nothing is open, and
     * [autoplay]ing for its ▶ key and a spoken "play". False when there's none on record or it's
     * been uninstalled since.
     */
    suspend fun reopenLastMedia(autoplay: Boolean = false): Boolean {
        val ctx = context ?: return false
        val last = CarSettings.lastMedia.value?.let(ComponentName::unflattenFromString) ?: return false
        val app = withContext(Dispatchers.IO) { MediaCatalog.installed(ctx) }
            .let { catalog -> webFor(last, catalog) ?: catalog.firstOrNull { it.component == last } ?: xonosFor(last, catalog) } ?: return false
        openMedia(app, autoplay = autoplay)
        return true
    }

    /** Closes the media app from the car: stops what it's playing and drops the connection. */
    /** The music app the driver closed from its menu; the app beside the map doesn't reopen it by itself. */
    var closedMedia: ComponentName? = null
        private set

    fun closeMedia() {
        closedMedia = _mediaApp.value?.component
        mediaAutoPicked = false
        expectMusic(false)
        if (media.now.value.playing) media.togglePlay()
        media.disconnect()
        _mediaApp.value = null
        autoplayPending = false
    }

    /**
     * Called once the media app is connected, and again as the sound's way to the car changes:
     * starts playback if that was asked for. The drive's own pick waits for its sound to have a way
     * to the car ([autoplayWaits]): started at once, it played on the phone's speaker in the driver's
     * pocket for as long as Automatic decided or Android's question waited for myCar on screen. What
     * the driver opens plays at once.
     */
    fun autoplayIfDue() {
        if (!autoplayPending) return
        // Playing already, whatever started it (its own page, the car's keys, the phone): done. A
        // pause given in the page afterwards stays, instead of Autoplay starting it again later.
        if (media.now.value.state == android.media.session.PlaybackState.STATE_PLAYING) {
            autoplayPending = false
            return
        }
        // The driver played, paused or picked something meanwhile: their choice stays. Counted from
        // the first call here, which comes once the app is connected: a ▶ pressed while it was still
        // connecting reached no app, and it cancelled Autoplay with nothing playing.
        if (autoplayAsks < 0) {
            autoplayAsks = media.askMark()
        } else if (media.askedSince(autoplayAsks)) {
            autoplayPending = false
            // The music asked for may still be coming (a web page that loads at its first key): the
            // column kept for it stays, with 20 s from now after a wait for the Bluetooth. Closed here,
            // the map widened and narrowed again when that music came.
            if (autoplayBluetoothSince != 0L && _musicStarting.value) {
                musicWait.removeCallbacks(musicWaitOver)
                musicWait.postDelayed(musicWaitOver, MUSIC_START_MS)
            }
            io.github.sirionero.mycar.MyCarLog.i("media: Autoplay left alone - the music was asked for something meanwhile")
            return
        }
        // A car that brought no way out for the music within the minute: no music by itself a long
        // while later. Checked first, whatever the way is now: when the car's Bluetooth music comes
        // back is the very moment Autoplay would otherwise play.
        val now = android.os.SystemClock.elapsedRealtime()
        if (autoplayBluetoothSince != 0L && now - autoplayBluetoothSince > AUTOPLAY_BLUETOOTH_MS) {
            autoplayPending = false
            if (_musicStarting.value) expectMusic(false)
            io.github.sirionero.mycar.MyCarLog.i("media: Autoplay given up - no way out for the music but the phone's speaker for ${AUTOPLAY_BLUETOOTH_MS / 1000} s")
            return
        }
        val bluetoothOut = io.github.sirionero.mycar.audio.CarAudioRoute.bluetoothOut.value
        if (autoplayAutoPicked && autoplayWaits(
                deciding = io.github.sirionero.mycar.audio.CarAudioRoute.deciding.value,
                throughMyCar = CarSettings.pipeAudio.value,
                captureOn = CarAudio.capture.value == CarAudio.Capture.ON,
                straightIn = _mediaApp.value?.component?.let(Xonos::isInside) == true && CarAudio.direct.value,
                bluetoothOut = bluetoothOut,
            )
        ) {
            val forBluetooth = !bluetoothOut && !CarSettings.pipeAudio.value &&
                !io.github.sirionero.mycar.audio.CarAudioRoute.deciding.value
            if (!forBluetooth) {
                // Automatic deciding, or Android's question: as before, with no limit.
                autoplayBluetoothSince = 0L
                io.github.sirionero.mycar.MyCarLog.i("media: Autoplay waits for the sound to have a way to the car")
                return
            }
            if (autoplayBluetoothSince == 0L) {
                autoplayBluetoothSince = now
                // The column kept for the music ("Starting …") lasts as long as this wait may: run out at
                // 20 s, the map widened and narrowed again once the music came - two new surfaces.
                if (_musicStarting.value) {
                    musicWait.removeCallbacks(musicWaitOver)
                    musicWait.postDelayed(musicWaitOver, AUTOPLAY_BLUETOOTH_MS)
                }
            }
            io.github.sirionero.mycar.MyCarLog.i("media: Autoplay waits for the car's Bluetooth music (or a cable), not to play on the phone's speaker")
            return
        }
        // The car's last drive ended on its own source - the driver picked its radio and stayed there
        // (see CarSource) - its music goes through myCar now, and the driver keeps the car's radio until
        // the phone plays (radioFirst): the radio stays on, as a car radio comes back on the source it
        // was left on. Without that choice the car is switched to myCar as the capture starts anyway, so
        // leaving the music off only left it quiet, radio gone too. Over Bluetooth the car picks its
        // source itself, as before.
        if (autoplayAutoPicked && CarSettings.pipeAudio.value && CarSettings.radioFirst.value && CarSource.lastDriveOnCarSource()) {
            autoplayPending = false
            if (_musicStarting.value) expectMusic(false)
            io.github.sirionero.mycar.MyCarLog.i("media: Autoplay: last drive ended on the car's own source - it stays on")
            return
        }
        // The assistant up (listening, answering): the drive's pick doesn't start the music over it. It
        // plays as the assistant closes (AutoplayWhenReady calls again then).
        if (autoplayAutoPicked && CarAssistant.state.value.phase != CarAssistant.Phase.IDLE) {
            io.github.sirionero.mycar.MyCarLog.i("media: Autoplay waits for the assistant to close")
            return
        }
        autoplayPending = false
        // After a wait for the Bluetooth, the song has its own 20 s from the Play to say itself.
        if (autoplayBluetoothSince != 0L && _musicStarting.value) {
            musicWait.removeCallbacks(musicWaitOver)
            musicWait.postDelayed(musicWaitOver, MUSIC_START_MS)
        }
        if (!media.now.value.playing) media.togglePlay()
    }

    /**
     * Cinema opened (CinemaMusic): an Autoplay still waiting doesn't start the music later. When the
     * car's Bluetooth came back within the minute, it took the speakers from the film.
     */
    fun dropAutoplay() {
        if (!autoplayPending) return
        autoplayPending = false
        io.github.sirionero.mycar.MyCarLog.i("media: Autoplay left alone - Cinema opened")
    }

    /** How long the drive's Autoplay waits for a Bluetooth output on the Bluetooth way. */
    private const val AUTOPLAY_BLUETOOTH_MS = 60_000L

    /**
     * A player really playing on the phone, whose sound takes the car's speakers from its radio (see
     * [CarAudio.playerPlaying]): the car's music app, Cinema's film, a site open in Cinema or the
     * browser open full screen, or any app's media session, read with the notification access the
     * music asks for anyway. Not a map's voice, a page in a part of the Home or the assistant, which
     * have no player. From the capture's thread: the sessions, a call to Android, are asked at most
     * every half second.
     */
    private fun aPlayerPlays(): Boolean {
        if (mediaOrNull()?.now?.value?.state == PlaybackState.STATE_PLAYING) return true
        // Cinema's own player says whether its film plays. A site open in Cinema, or the browser full
        // screen, has no player to ask: the driver opened it on the car's screen, so its sound - a video,
        // a web radio - is theirs. Left out, it stayed silent after the radio for the rest of the drive:
        // the phone muted by the capture, the car on its radio, its own GAIN starting nothing.
        val film = CinemaNow.playing
        if (film != null) {
            if (film()) return true
        } else if (CinemaNow.pause != null) {
            return true
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - sessionsAskedAt < SESSIONS_EVERY_MS) return sessionsPlaying
        sessionsAskedAt = now
        val ctx = context ?: return false
        sessionsPlaying = try {
            ctx.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(ctx, MyCarNotificationListener::class.java))
                .any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        } catch (e: SecurityException) {
            // No notification access: the car's music app and Cinema are what myCar sees.
            false
        }
        return sessionsPlaying
    }

    @Volatile private var sessionsAskedAt = 0L
    @Volatile private var sessionsPlaying = false
    private const val SESSIONS_EVERY_MS = 500L

    // --- start and stop ------------------------------------------------------------------

    /** Brings back whatever was in use last time. Safe to call repeatedly. */
    suspend fun autostart(frame: CarEnvironment.Frame, withMedia: Boolean = true) {
        val ctx = context ?: return

        if (!navStopped && nav.status.value.phase == CarAppConnection.Phase.IDLE) {
            val navApps = withContext(Dispatchers.IO) { CarAppCatalog.installed(ctx) }
                .filter { it.isNavigation }
            _hasNavApps.value = navApps.isNotEmpty()
            // Never auto-pick one already known to fail outright - a driver who plugs in should
            // land on a map, not on the one tile marked with a red X. Nor one that turned myCar down.
            val candidates = navApps.filterNot { KnownApps.wontStart(ctx, it.component.packageName) }
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
            // The phone's own app of a service whose web player takes its place in the car (its app
            // ticked to stay off the grid): the music goes on in that player instead, the app paused.
            val web = playing?.takeUnless { phonePreview }?.let { webFor(it.component, catalog) }
            if (web != null) {
                pausePhoneMedia("its web player takes its place in the car")
                openMedia(web, autoplay = true, auto = true)
            } else if (playing != null) {
                openMedia(playing, auto = true)
            } else {
                val last = CarSettings.lastMedia.value?.let(ComponentName::unflattenFromString)
                val app = last?.let { component -> webFor(component, catalog) ?: catalog.firstOrNull { it.component == component } ?: xonosFor(component, catalog) }
                if (app != null) openMedia(app, autoplay = CarSettings.autoplay.value && !phonePreview, auto = true)
            }
        }
        _mediaDecided.value = true
    }

    /**
     * The car's music is Xonos inside myCar: the one open, or, until one opens, the one used last -
     * or none yet, with Xonos the music app there is.
     */
    private fun xonosIsTheMusic(): Boolean {
        if (!Xonos.enabled.value || !Xonos.setUp.value || Xonos.appTakesOver.value) return false
        val app = _mediaApp.value?.component
            ?: CarSettings.lastMedia.value?.let(ComponentName::unflattenFromString)
            ?: return true
        return Xonos.isInside(app) || app.packageName == Xonos.APP_PACKAGE
    }

    /**
     * The other Xonos, for one used last that the car hasn't got this drive: Xonos inside myCar for
     * the Xonos app once it's set up, and the app for it while the app takes over (see [InsideOrApp]).
     */
    private fun xonosFor(last: ComponentName, catalog: List<MediaApp>): MediaApp? = when {
        last.packageName == Xonos.APP_PACKAGE -> catalog.firstOrNull { Xonos.isInside(it.component) }
        Xonos.isInside(last) -> catalog.firstOrNull { it.component.packageName == Xonos.APP_PACKAGE }
        else -> null
    }

    /**
     * A music service's web player for its own app used last, once that app is ticked to stay off the
     * car's grid (see [WebMusic.replacing]): the drive opens the one the driver keeps in the car.
     */
    private fun webFor(last: ComponentName, catalog: List<MediaApp>): MediaApp? =
        WebMusic.replacing(last.packageName)?.let { service -> catalog.firstOrNull { WebMusic.serviceFor(it.component) === service } }

    /**
     * The Xonos app took Xonos's place for the drive (see [InsideOrApp]): if Xonos inside myCar was
     * the car's music, the app is now, playing if the music was meant to play. Not remembered: the
     * next drive tries Xonos inside myCar first again.
     */
    suspend fun xonosAppTakesOver() {
        val ctx = context ?: return
        val inside = _mediaApp.value?.takeIf { Xonos.isInside(it.component) } ?: return
        val app = withContext(Dispatchers.IO) { MediaCatalog.installed(ctx) }
            .firstOrNull { it.component.packageName == Xonos.APP_PACKAGE } ?: return
        if (_mediaApp.value !== inside) return
        // Xonos's column still kept for its first song: kept for the app's (Xonos gives way 20 s in, as
        // the wait ends: closed and opened again, the map would be lent two more surfaces).
        val waiting = _musicStarting.value
        // The drive's own Autoplay, still waiting, stays the drive's: the app's waits for the sound's way
        // to the car too. Xonos's player (full screen, its tile) clears mediaAutoPicked, and the app then
        // played at once, on the phone's speaker. The preview on the phone (Settings, no car) plays nothing
        // by itself, as at autostart: the Autoplay setting started the app there, on the phone's speaker.
        openMedia(app, autoplay = (CarSettings.autoplay.value && !phonePreview) || media.now.value.playing, remember = false, auto = mediaAutoPicked || (autoplayPending && autoplayAutoPicked))
        if (waiting) expectMusic(true)
    }

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

    /**
     * [again]: a second look [REPAUSE_MS] later, while it says yes (see [pauseAgainLater]). For the
     * car leaving and the car taking its own source; not for screen sharing, which keeps an app.
     */
    fun pausePhoneMedia(why: String = "the car left", keep: String? = null, again: (() -> Boolean)? = null) {
        // Nothing paused for the car's moment on its speakers plays again after this.
        pausedForCar.forget()
        val ctx = context ?: return
        val manager = ctx.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(ctx, MyCarNotificationListener::class.java)
        val playing = try {
            manager.getActiveSessions(listener).filter { it.playbackState?.state in SOUNDING && it.packageName != keep }
        } catch (e: SecurityException) {
            null
        }
        if (playing == null) {
            // No notification access: the app myCar itself is driving is all it can reach - unless
            // it's the one kept (Xonos inside myCar, which screen sharing doesn't silence).
            if (keep != null && _mediaApp.value?.component?.packageName == keep) return
            mediaOrNull()?.let { if (it.now.value.playing) it.togglePlay() }
            return
        }
        playing.forEach { runCatching { it.transportControls.pause() } }
        if (playing.isNotEmpty()) {
            io.github.sirionero.mycar.MyCarLog.i("media: paused ${playing.joinToString { it.packageName }}: $why")
            if (again != null) pauseAgainLater(playing, why, again)
        }
    }

    /**
     * An app paused as the car left can start again by itself a moment later, out of the phone's
     * speaker: Deezer did, after the car was switched off (Gearslip issue #39). Those of [paused]
     * sounding again [REPAUSE_MS] later are paused once more - while [still] says so.
     */
    private fun pauseAgainLater(paused: List<android.media.session.MediaController>, why: String, still: () -> Boolean) {
        repause.postDelayed({
            // Only out of the phone's own speaker. With the car's Bluetooth music (or a cable) still
            // there the car itself may have asked for it, and when that goes Android pauses the apps.
            if (!still() || io.github.sirionero.mycar.audio.CarAudioRoute.musicOutsideThePhone()) return@postDelayed
            val again = paused.filter { it.playbackState?.state in SOUNDING }
            if (again.isEmpty()) return@postDelayed
            again.forEach { runCatching { it.transportControls.pause() } }
            io.github.sirionero.mycar.MyCarLog.i("media: paused ${again.joinToString { it.packageName }} again: it started by itself after $why")
        }, REPAUSE_MS)
    }

    private val repause = android.os.Handler(android.os.Looper.getMainLooper())

    /** How long after pausing for the car to look again for an app that started by itself. */
    private const val REPAUSE_MS = 2_000L

    /** For the second look after the car left: none once a new drive is under way (its own Autoplay decides). */
    fun noDriveUnderWay(): Boolean = io.github.sirionero.mycar.SessionStatus.state.value.phase.let {
        it != io.github.sirionero.mycar.SessionStatus.Phase.CONNECTING && it != io.github.sirionero.mycar.SessionStatus.Phase.PROJECTING
    }

    /**
     * For the second look while the car is on its own source: none if the driver asks the music for
     * anything meanwhile (▶ on the car's screen or wheel, by voice), counted from now.
     */
    fun noMusicAskedFromNow(): () -> Boolean {
        val media = mediaOrNull() ?: return { true }
        val mark = media.askMark()
        return { !media.askedSince(mark) }
    }

    /**
     * What [pausePhoneMediaForNow] paused, for [resumePhoneMedia]: the sessions (or, without
     * notification access, the app myCar drives itself, paused through it), where the driver's asks of
     * the music stood ([CarMedia.askMark]), and the car's music app then (another one picked since is
     * the driver's choice).
     */
    private val pausedForCar = PausedForCar.Memory<android.media.session.MediaController> { it.sessionToken }

    /**
     * The car takes its speakers for a moment (AudioLink: a call on the car, its own voice or
     * announcements) while the phone's music streams through myCar: what plays is paused, rather
     * than playing on unheard, and remembered exactly, for [resumePhoneMedia] at the car's GAIN. Only
     * what plays now: a player Android paused for a call itself resumes by itself after it.
     * From the session's reading thread.
     */
    fun pausePhoneMediaForNow() {
        val ctx = context ?: return
        val media = mediaOrNull()
        val mark = media?.askMark()
        val app = _mediaApp.value?.component
        val manager = ctx.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(ctx, MyCarNotificationListener::class.java)
        val playing = try {
            manager.getActiveSessions(listener).filter { it.playbackState?.state in SOUNDING }
        } catch (e: SecurityException) {
            null
        }
        if (playing == null) {
            // No notification access: the app myCar itself is driving is all it can reach.
            val own = media?.now?.value?.playing == true
            if (own) {
                media?.pauseForCar()
                io.github.sirionero.mycar.MyCarLog.i("media: paused the music app while the car has the speakers for a moment")
            }
            pausedForCar.paused(emptyList(), own, mark, app)
            return
        }
        playing.forEach { runCatching { it.transportControls.pause() } }
        // With what an earlier moment of the car's paused, if it hasn't played again yet (PausedForCar.Memory).
        pausedForCar.paused(playing, own = false, askMark = mark, app = app)
        if (playing.isNotEmpty()) {
            io.github.sirionero.mycar.MyCarLog.i("media: paused ${playing.joinToString { it.packageName }} while the car has the speakers for a moment")
        }
    }

    /**
     * The car gave its speakers back: what [pausePhoneMediaForNow] paused plays again - only that,
     * only what is still paused, and not if the driver asked the music for anything or picked
     * another music app meanwhile, or the drive ended (see [PausedForCar]). During a call, once it's over.
     */
    fun resumePhoneMedia() {
        // What waits now, on the session's thread: a moment of the car's after this GAIN waits for its own.
        val paused = pausedForCar.given() ?: return
        repause.post { resumeWhenFree(paused, PausedForCar.CALL_CHECKS) }
    }

    private fun resumeWhenFree(paused: PausedForCar.Paused<android.media.session.MediaController>, waitsLeft: Int) {
        // Forgotten (the drive ended, the radio, the car left), or the car took its speakers again
        // since: the GAIN after that plays it.
        if (!pausedForCar.waits(paused)) return
        val media = mediaOrNull()
        val mark = paused.askMark
        val asked = (mark != null && media != null && media.askedSince(mark)) || _mediaApp.value?.component != paused.app
        when (PausedForCar.atGain(paused = true, driverAsked = asked, inCall = inCall(), waitsLeft = waitsLeft)) {
            PausedForCar.Then.WAIT -> {
                if (waitsLeft == PausedForCar.CALL_CHECKS) {
                    io.github.sirionero.mycar.MyCarLog.i("media: the car gave the speakers back during a call - the music waits for its end")
                }
                repause.postDelayed({ resumeWhenFree(paused, waitsLeft - 1) }, PausedForCar.CALL_CHECK_MS)
                return
            }
            PausedForCar.Then.DROP -> {
                if (!pausedForCar.done(paused)) return
                val why = if (asked) "the driver asked it for something since" else "the call went on too long"
                io.github.sirionero.mycar.MyCarLog.i("media: the music paused for the car stays paused: $why")
                return
            }
            // Not if the car took its speakers again in the meantime.
            PausedForCar.Then.PLAY -> if (!pausedForCar.done(paused)) return
        }
        if (paused.own && media != null && media.now.value.state == PlaybackState.STATE_PAUSED) {
            media.playForCar()
            io.github.sirionero.mycar.MyCarLog.i("media: resumed the music app: the car gave the speakers back")
        }
        val waiting = paused.players.filter { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        waiting.forEach { runCatching { it.transportControls.play() } }
        if (waiting.isNotEmpty()) {
            io.github.sirionero.mycar.MyCarLog.i("media: resumed ${waiting.joinToString { it.packageName }}: the car gave the speakers back")
        }
    }

    /**
     * A phone call is on (ringing, talking, on hold; a calling app's too, through Telecom). Not just
     * "in communication": an app can leave the phone in that mode for good (see CarAudioRoute), and
     * the music would never play again.
     */
    private fun inCall(): Boolean {
        val ctx = context ?: return false
        val telecom = ctx.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            runCatching { ctx.getSystemService(android.telecom.TelecomManager::class.java)?.isInCall == true }.getOrDefault(false)
        return telecom || when (ctx.getSystemService(android.media.AudioManager::class.java)?.mode) {
            android.media.AudioManager.MODE_IN_CALL, android.media.AudioManager.MODE_RINGTONE -> true
            else -> false
        }
    }

    /** The car went away: nothing should keep running for a screen that no longer exists. */
    fun shutdown() {
        if (holders == 0 || --holders > 0) return
        // The drive's moment on the car's speakers ends with it: nothing plays again after.
        pausedForCar.forget()
        context?.let { io.github.sirionero.mycar.notify.CarMotion.stop(it) }
        io.github.sirionero.mycar.call.CarCalls.stop()
        runCatching { nav.disconnect() }
        runCatching { media.disconnect() }
        runCatching { browse.disconnect() }
        // A reply the driver was confirming goes out, any other is dropped, as when the microphone
        // is pressed: left as it was, its card came back at the next connection, stuck. Only once its
        // countdown runs: still read back, the driver hadn't heard it yet.
        if (VoiceReply.active) {
            val reply = VoiceReply.state.value
            if (reply.phase == VoiceReply.Phase.CONFIRMING && reply.until > 0L) VoiceReply.sendNow() else VoiceReply.cancel()
        }
        CarAssistant.cancel()
        io.github.sirionero.mycar.home.HomeAssistant.release()
        InsideOrApp.stop()
        if (xonosHeld) Xonos.repo.release()
        xonosHeld = false
        // The way Plancia's page came through the Plancia app, this drive.
        io.github.sirionero.mycar.bridge.BridgeProxy.stop()
        // About 900 MB that nothing needs once the car's gone - and not loaded after it either.
        speechWarm.removeCallbacks(warmSpeech)
        speechWarm.removeCallbacks(warmFinal)
        io.github.sirionero.mycar.speech.SpeechEngine.unload("at the end of the drive")
        CarAudio.release()
        _mediaApp.value = null
        _mediaDecided.value = false
        expectMusic(false)
        mediaAutoPicked = false
        closedBrowse = emptySet()
        closedMedia = null
        // A call waiting to be confirmed belongs to this drive, not to the car's next arrival.
        CallPrompt.pending.value = null
        // A toast or the voice help up as the drive ended came back at the start of the next one.
        CarToasts.clearAll()
        VoiceHelp.hide()
        CarSettings.forgetOpenWhatsNew()
        // The car's Settings open at the top again, next drive.
        SettingsScroll.forget()
        media.forgetLosses()
        clearSectionCache()
        navStopped = false
    }
}
