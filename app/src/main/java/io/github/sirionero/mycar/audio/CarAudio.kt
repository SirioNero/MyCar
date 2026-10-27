package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.MyCarLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Joins the two ends of the audio path: [AudioCaptureService] on the phone, which hears what the
 * phone is playing, and the car's audio channel, which is where it has to end up.
 *
 * Capture belongs to the car session, not to any one app. Android grants it through a consent
 * dialog that cannot be answered in advance or remembered, so myCar asks once, when a head unit
 * first offers a sink to play through, and then holds that grant for as long as the car is
 * connected. Switching between media apps, or starting playback from the phone, costs nothing
 * further: the capture is by audio usage rather than by app, so whatever the phone plays as media
 * goes to the car.
 *
 * myCar's own music - Xonos inside myCar - needs no capture: it goes straight down the same sink
 * ([openDirect], [DirectFeed]). While the car's music is that, the consent question waits
 * ([consentWaits]) until another app's sound has to reach the car ([captureNow]).
 */
object CarAudio {

    /** What the head unit's media sink accepts, and how to hand it audio. */
    interface Link {
        val sampleRate: Int
        val channels: Int
        fun start()
        fun stop()
        fun write(pcm: ByteArray, size: Int)
    }

    enum class Capture { OFF, ASKING, ON, DENIED }

    private val _capture = MutableStateFlow(Capture.OFF)
    val capture: StateFlow<Capture> = _capture.asStateFlow()

    /** "-12 dB" style summary of what was last heard, for the media screen. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Set by the activity: shows the permission and consent dialogs, which only it can. */
    var onConsentRequested: (() -> Unit)? = null

    /** Whether the driver has asked for the car to be fed at all; see CarSettings.pipeAudio. */
    @Volatile private var wanted = false

    /**
     * Still wanted when the consent dialog comes back: it may be answered long after it went up,
     * once the drive is over or the sound has gone to Bluetooth, and then must start nothing.
     */
    val stillWanted: Boolean get() = wanted

    @Volatile private var link: Link? = null

    /** myCar's own music on its way to the car, once it has played this drive. */
    @Volatile private var feed: DirectFeed? = null

    private val _direct = MutableStateFlow(false)

    /** Whether myCar's own player can play straight into the car now: the sound goes through myCar, and the car has a sink. */
    val direct: StateFlow<Boolean> = _direct.asStateFlow()

    /**
     * True while the car's music is myCar's own, which needs no capture: the consent question waits
     * for [captureNow]. Set by the car session.
     */
    @Volatile var consentWaits: () -> Boolean = { false }

    /** Rate and channel count the capture should use: the car's, or a sensible default. */
    val sampleRate get() = link?.sampleRate ?: 48_000
    val channels get() = link?.channels ?: 2

    fun attach(link: Link) {
        this.link = link
        MyCarLog.i("audio: the car offered a sink at ${link.sampleRate}Hz x${link.channels}")
        when {
            _capture.value == Capture.ON -> link.start()
            wanted && consentWaits() -> MyCarLog.i("audio: the car's music is myCar's own - Android isn't asked for now")
            wanted -> ask()
        }
        updateDirect()
    }

    fun detach(link: Link) {
        if (this.link !== link) return
        this.link = null
        feed?.takeIf { it.link === link }?.stop()
        feed = null
        updateDirect()
    }

    /**
     * The car wants sound. Safe to call as often as the session likes: the consent dialog appears
     * at most once, and only once there is a sink to play through - asking on the bench, where the
     * sound has nowhere to go, would put a system dialog in front of the driver for nothing.
     */
    fun request() {
        wanted = true
        updateDirect()
        when (_capture.value) {
            Capture.ON, Capture.ASKING, Capture.DENIED -> return
            Capture.OFF -> if (link == null) {
                MyCarLog.i("audio: waiting for the car to offer an audio sink")
            } else if (!consentWaits()) {
                ask()
            }
        }
    }

    /** Another app's sound has to reach the car: ask for the capture now, if it waited ([consentWaits]). */
    fun captureNow() {
        if (wanted && link != null && _capture.value == Capture.OFF) ask()
    }

    /**
     * Where myCar's own music at [sampleRate] x [channels] goes into the car, while the sound goes
     * through myCar; null otherwise, and it plays on the phone (over Bluetooth, to the car).
     */
    internal fun openDirect(sampleRate: Int, channels: Int): DirectFeed.Writer? {
        val link = link ?: return null
        if (!wanted) return null
        val running = feed?.takeIf { it.link === link } ?: DirectFeed(link, { _capture.value == Capture.ON }).also {
            feed = it
            it.start()
        }
        // Asks the car for its speakers, unless the capture already has them.
        link.start()
        return running.open(sampleRate, channels)
    }

    private fun updateDirect() {
        _direct.value = wanted && link != null
    }

    private fun ask() {
        _capture.value = Capture.ASKING
        val request = onConsentRequested
        if (request == null) {
            MyCarLog.w("audio: nothing on the phone can ask for capture consent")
            _capture.value = Capture.DENIED
        } else {
            request()
        }
    }

    /** The car went away. Everything is torn down; the next connection asks again. */
    fun release() {
        wanted = false
        AudioCaptureService.stop()
        _capture.value = Capture.OFF
        feed?.let {
            it.stop()
            feed = null
            link?.stop()
        }
        updateDirect()
    }

    fun denied() {
        _capture.value = Capture.DENIED
    }

    private val _micBlocked = MutableStateFlow(false)

    /** The microphone permission is off for good: Android refuses without asking, so say why. */
    val micBlocked: StateFlow<Boolean> = _micBlocked.asStateFlow()

    fun micBlocked() {
        _micBlocked.value = true
        denied()
    }

    /** The driver turned the permission back on mid-drive: ask for capture again. */
    fun micAllowed() {
        if (!_micBlocked.value) return
        _micBlocked.value = false
        if (_capture.value == Capture.DENIED) {
            _capture.value = Capture.OFF
            if (wanted) request()
        }
    }

    internal fun captureStarted() {
        _capture.value = Capture.ON
        link?.start()
    }

    internal fun captureStopped() {
        // myCar's own music, if it has played, keeps the car's speakers.
        if (feed == null) link?.stop()
        _level.value = 0f
        if (_capture.value == Capture.ON) _capture.value = Capture.OFF
    }

    internal fun deliver(pcm: ByteArray, size: Int, peak: Float) {
        _level.value = peak
        // While myCar's own music reaches the car, it alone feeds it.
        if (feed?.feeding == true) return
        link?.write(pcm, size)
    }
}
