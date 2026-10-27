package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.ServiceDiscovery
import io.github.sirionero.mycar.Wire

/**
 * The phone end of the head unit's media audio sink, the audio twin of the video channel:
 *
 *   -> CHANNEL_OPEN_REQUEST   <- CHANNEL_OPEN_RESPONSE
 *   -> Setup { PCM }          <- Config { status, max_unacked, configuration_indices }
 *   -> AudioFocusRequest      <- AudioFocusResponse           (control channel)
 *   -> Start { session_id, configuration_index }
 *   -> Data { 8-byte timestamp, PCM }...   <- Ack { session_id, ack }
 *   -> Stop
 *
 * Inverted from aasdk's AudioMediaSinkService. Bench-verified only as far as capture goes:
 * nothing on this side of the wire has met a head unit yet.
 */
class AudioLink(
    private val service: ServiceDiscovery.AudioService,
    private val sendOnChannel: (messageId: Int, body: ByteArray) -> Unit,
    private val sendControl: (messageId: Int, body: ByteArray) -> Unit,
    /** Log name: "audio" for the media sink, "guidance" for the directions sink. */
    private val tag: String = "audio",
    private val sessionId: Int = SESSION_ID,
    /** Media asks the car for focus; directions just start, and the car ducks music under them. */
    private val asksFocus: Boolean = true,
    private val onReady: (AudioLink) -> Unit = { CarAudio.attach(it) },
    private val onClose: (AudioLink) -> Unit = { CarAudio.detach(it) },
    /** The car took the sound back after giving it, as when the driver switches to the radio. */
    private val onTakenBack: () -> Unit = {},
) : CarAudio.Link {

    private var configIndex = 0
    @Volatile private var ready = false
    @Volatile private var started = false
    @Volatile private var focusGranted = false
    @Volatile private var focusRequested = false
    /**
     * The car has the speakers for a while - a call, its own voice, the phone's assistant over
     * Bluetooth - and will give them back: the stream waits, the focus still asked for.
     */
    @Volatile private var heldBack = false
    /** While the car isn't taking the sound: how much quiet, then sound, the capture has had (see [listenForPlayback]). */
    private var quietBytes = 0L
    private var soundBytes = 0L
    private var armed = false
    @Volatile private var inFlight = 0
    // The Uconnect asks for 4 on its video channel; assume the same until its Config says otherwise.
    @Volatile private var maxUnacked = 4
    @Volatile private var lastAckAt = 0L
    private var stalls = 0
    private var chunks = 0
    private var bytes = 0L
    private var dropped = 0
    @Volatile private var configTimer: Thread? = null
    private val ackLock = Object()
    /** Only for a sink that takes AAC and nothing else; PCM sinks get the capture as it is. */
    @Volatile private var aac: AacEncoder? = null
    private val aacSink get() = service.codecType == ServiceDiscovery.CODEC_AAC_LC || service.codecType == ServiceDiscovery.CODEC_AAC_LC_ADTS

    private val config get() = service.configs.getOrNull(configIndex) ?: service.configs.firstOrNull()
    override val sampleRate get() = config?.sampleRate ?: 48_000
    override val channels get() = config?.channels ?: 2

    val channelId get() = service.serviceId

    fun opened(status: Int) {
        MyCarLog.i("$tag: <- ChannelOpenResponse: status=$status")
        if (status != 0) {
            MyCarLog.w("$tag: the head unit refused the $tag channel (status $status)")
            return
        }
        // Setup { required MediaCodecType type = 1 }: the codec the car advertised for this sink
        // (aasdk's current protos, and LIVI reads it as the codec). myCar used to send 0 here,
        // read from an older proto as a config index; 0 is no codec at all, and the 2024 VW
        // answered UNEXPECTED_MESSAGE (id 255), never acked a chunk and played nothing.
        sendOnChannel(MSG_SETUP, Protobuf.varintField(1, service.codecType.toLong()))
        MyCarLog.i("$tag: -> Setup(codec=${service.codecName}) on channel ${service.serviceId}")
        armConfigFallback()
    }

    /**
     * The 2018 Uconnect, a Dacia ULC 4.5 and the 2024 VW answered the old Setup (codec 0) with
     * UNEXPECTED_MESSAGE (id 255, no body) rather than a Config, so the sink never reached READY.
     * [MSG_SETUP_ANSWER] still takes that as the go-ahead, in case a car rejects the real codec too.
     *
     * Service discovery already told us everything a Config would have: the sink's rate, channels
     * and its one configuration. So for a car that answers nothing at all, wait a moment and then
     * assume the advertised configuration. If that guess is wrong the head unit simply will not
     * ack, which the write path already reports.
     */
    private fun armConfigFallback() {
        val timer = Thread({
            try {
                Thread.sleep(CONFIG_WAIT_MS)
            } catch (e: InterruptedException) {
                return@Thread
            }
            useAdvertisedSink("no usable Config after ${CONFIG_WAIT_MS}ms")
        }, "mycar-audio-config").apply { isDaemon = true }
        configTimer = timer
        timer.start()
    }

    @Synchronized
    private fun useAdvertisedSink(why: String) {
        if (ready) return
        configTimer?.interrupt()
        MyCarLog.i(
            "$tag: $why - using the sink the head unit advertised " +
                "(${sampleRate}Hz x$channels, config_index=0, max_unacked=$maxUnacked)",
        )
        configIndex = 0
        ready = true
        onReady(this)
    }

    fun onMessage(messageId: Int, body: ByteArray) {
        when (messageId) {
            MSG_CHANNEL_OPEN_RESPONSE -> opened(Protobuf.readInt32Field(body, 1) ?: 0)

            MSG_CONFIG -> {
                val fields = Wire.fields(body)
                val status = Wire.varint(fields, 1)?.toInt()
                val window = Wire.varint(fields, 2)?.toInt()
                val indices = fields.filter { it.number == 3 && it.wireType == 0 }.map { it.varint.toInt() }
                MyCarLog.i("$tag: <- Config status=$status max_unacked=$window indices=$indices")
                if (window != null) maxUnacked = maxOf(1, window)
                configIndex = indices.firstOrNull() ?: 0
                if (status == STATUS_READY) {
                    configTimer?.interrupt()
                    ready = true
                    MyCarLog.i("$tag: sink ready - ${sampleRate}Hz x$channels ${config?.bits}bit")
                    onReady(this)
                } else {
                    MyCarLog.w(
                        "$tag: the sink is not ready (status=$status, 1=WAIT 2=READY) - " +
                            "nothing will be sent until it is",
                    )
                }
            }

            MSG_ACK -> {
                val acked = Wire.varint(Wire.fields(body), 2)?.toInt() ?: 1
                synchronized(ackLock) {
                    inFlight = maxOf(0, inFlight - maxOf(1, acked))
                    lastAckAt = System.currentTimeMillis()
                    ackLock.notifyAll()
                }
            }

            // Whatever else the head unit answers Setup with, we need its bytes to identify it.
            else -> if (messageId == MSG_SETUP_ANSWER && body.isEmpty()) {
                useAdvertisedSink("<- empty Setup answer (id $messageId)")
            } else {
                MyCarLog.i("$tag: <- unhandled message id=$messageId (${body.size} bytes)")
                MyCarLog.hex("   audio msg", body, limit = 64)
            }
        }
    }

    /**
     * The head unit's answer to our AudioFocusRequest, and its word whenever that changes. Until
     * it grants focus the sink is wired to whatever else the car is playing - the radio, usually -
     * and anything we send is discarded, so [start] waits here rather than streaming into nothing.
     *
     * A car that takes the speakers for a while (a call, the phone's assistant over Bluetooth, its
     * own voice) gives them back with a GAIN, so the stream waits for it with the focus still
     * asked for. Letting go there, as myCar used to, told the car the phone was done: no GAIN came,
     * and the music stayed off for the rest of the drive. Ducking keeps it playing under the car's
     * own sound. Only a LOSS - the driver picked the radio - lets go, and then the phone playing
     * again asks once more ([listenForPlayback]).
     */
    fun onFocus(state: Int) {
        val name = FOCUS_STATES[state] ?: "unknown($state)"
        MyCarLog.i("$tag: <- AudioFocusResponse state=$name")
        val ours = focusGranted || heldBack
        when (state) {
            FOCUS_STATE_GAIN, FOCUS_STATE_GAIN_TRANSIENT, FOCUS_STATE_GAIN_MEDIA_ONLY -> {
                focusGranted = true
                heldBack = false
                startStream()
            }
            FOCUS_STATE_LOSS_TRANSIENT_CAN_DUCK -> Unit
            FOCUS_STATE_LOSS_TRANSIENT, FOCUS_STATE_GAIN_TRANSIENT_GUIDANCE_ONLY -> {
                focusGranted = false
                hold()
            }
            else -> {
                stop()
                // Another source has the speakers for good: the phone's music shouldn't play on to no one.
                if (ours && state == FOCUS_STATE_LOSS) onTakenBack()
            }
        }
    }

    override fun start() {
        if (!ready || started) return
        // The car has the speakers for now; its GAIN starts the stream.
        if (heldBack) return
        if (asksFocus && !focusRequested) {
            focusRequested = true
            sendControl(MSG_FOCUS_REQUEST, Protobuf.varintField(1, FOCUS_GAIN.toLong()))
            MyCarLog.i("$tag: -> AudioFocusRequest(GAIN); waiting for the head unit to hand the sink over")
        }
        // Some head units grant the sink silently and never answer. Do not let that be the end of
        // it: start anyway, and let the acks say whether anything is being taken.
        startStream()
    }

    private fun startStream() {
        if (!ready || started) return
        sendOnChannel(
            MSG_START,
            Protobuf.varintField(1, sessionId.toLong()) + Protobuf.varintField(2, configIndex.toLong()),
        )
        inFlight = 0
        lastAckAt = System.currentTimeMillis()
        if (aacSink && aac == null) {
            aac = runCatching { AacEncoder(sampleRate, channels, adts = service.codecType == ServiceDiscovery.CODEC_AAC_LC_ADTS) }
                .onSuccess { MyCarLog.i("$tag: the car takes ${service.codecName} only - encoding to AAC") }
                .onFailure { MyCarLog.e("$tag: couldn't start the AAC encoder", it) }
                .getOrNull()
        }
        started = true
        armed = false
        MyCarLog.i("$tag: -> Start(session=$sessionId, config=$configIndex) - streaming ${sampleRate}Hz x$channels")
    }

    override fun stop() {
        val streaming = started
        // Held back, the stream is already stopped but the focus is still asked for: give it back.
        if (!streaming && !focusRequested) return
        started = false
        heldBack = false
        synchronized(ackLock) { ackLock.notifyAll() }
        runCatching {
            if (streaming) sendOnChannel(MSG_STOP, Protobuf.varintField(1, sessionId.toLong()))
            if (focusRequested) sendControl(MSG_FOCUS_REQUEST, Protobuf.varintField(1, FOCUS_RELEASE.toLong()))
        }
        focusRequested = false
        focusGranted = false
        aac?.release()
        aac = null
        MyCarLog.i("$tag: -> Stop, AudioFocusRequest(RELEASE) after $chunks chunks / $bytes bytes")
        chunks = 0
        bytes = 0
    }

    /** The car has the speakers for a while: stop sending, keeping the focus asked for. */
    private fun hold() {
        if (focusRequested) heldBack = true
        if (!started) return
        started = false
        synchronized(ackLock) { ackLock.notifyAll() }
        runCatching { sendOnChannel(MSG_STOP, Protobuf.varintField(1, sessionId.toLong())) }
        aac?.release()
        aac = null
        MyCarLog.i("$tag: -> Stop, the focus kept for the car's GAIN, after $chunks chunks / $bytes bytes")
        chunks = 0
        bytes = 0
    }

    override fun write(pcm: ByteArray, size: Int) {
        if (!started) {
            if (asksFocus && ready) listenForPlayback(pcm, size)
            return
        }
        val encoder = aac
        if (encoder == null) {
            send(pcm.copyOf(size))
            return
        }
        val frames = try {
            encoder.encode(pcm, size)
        } catch (e: Exception) {
            // A codec that dies mid-drive leaves the car silent, as it was before AAC; log it once.
            MyCarLog.e("$tag: AAC encoding failed - no more audio this drive", e)
            aac = null
            started = false
            return
        }
        frames.forEach(::send)
    }

    /** One Data message, once the car has room for it. */
    private fun send(payload: ByteArray) {
        // Wait for the car to catch up rather than throw the chunk away: a dropped chunk is a gap
        // the driver hears, while a short wait is soaked up by the capture buffer behind us.
        synchronized(ackLock) {
            while (started && inFlight >= maxUnacked) {
                val waited = System.currentTimeMillis() - lastAckAt
                if (waited > STALL_MS) {
                    // A lost ack must not silence the stream for good; after a stall assume the window cleared.
                    // A car that never acks (the 2024 VW, the Bolt) hits this every chunk: say so
                    // once, then now and then, so the rest of the log stays readable.
                    if (stalls++ % 500 == 0) {
                        MyCarLog.w("$tag: no ack for ${STALL_MS}ms with $inFlight in flight - reopening the window ($stalls times)")
                    }
                    inFlight = 0
                    break
                }
                ackLock.wait(STALL_MS - waited + 1)
            }
        }
        if (!started) {
            dropped++
            return
        }
        val timestamp = ByteArray(8)
        val micros = System.nanoTime() / 1_000
        for (i in 0 until 8) timestamp[i] = ((micros shr ((7 - i) * 8)) and 0xFF).toByte()
        try {
            sendOnChannel(MSG_DATA, timestamp + payload)
        } catch (e: java.io.IOException) {
            MyCarLog.e("$tag: the link died mid-stream", e)
            started = false
            return
        }
        synchronized(ackLock) { inFlight++ }
        chunks++
        bytes += payload.size
        // The first chunk proves the whole path; after that, an occasional line is enough to show
        // the car is still taking audio and to catch a window that has quietly closed.
        if (chunks == 1 || chunks % CHUNK_LOG_EVERY == 0) {
            MyCarLog.i(
                "$tag: -> $chunks chunks / $bytes bytes, inFlight=$inFlight/$maxUnacked, " +
                    "dropped=$dropped, focus=${if (focusGranted) "granted" else "not granted"}",
            )
        }
    }

    /**
     * The capture runs on while the car isn't taking the sound: it let go of it, or has it for a
     * call. Sound after a quiet spell is the phone playing again - the driver pressed play, the
     * music came back after the call - so ask the car for the speakers, as Android Auto does when
     * a media app starts. A car still busy says so, and the next ask waits for the next quiet
     * spell and the sound after it. Timed by the audio itself, which the capture delivers as it plays.
     */
    private fun listenForPlayback(pcm: ByteArray, size: Int) {
        if (!heard(pcm, size)) {
            soundBytes = 0
            quietBytes += size
            if (millis(quietBytes) >= QUIET_MS) armed = true
            return
        }
        quietBytes = 0
        soundBytes += size
        if (!armed || millis(soundBytes) < SOUND_MS) return
        armed = false
        focusRequested = true
        runCatching { sendControl(MSG_FOCUS_REQUEST, Protobuf.varintField(1, FOCUS_GAIN.toLong())) }
        MyCarLog.i("$tag: the phone is playing again - -> AudioFocusRequest(GAIN)")
    }

    /** Anything above a whisper in this 16-bit PCM: a paused player hands the capture zeros. */
    private fun heard(pcm: ByteArray, size: Int): Boolean {
        var i = 0
        while (i + 1 < size) {
            val sample = ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
            if (sample > WHISPER || sample < -WHISPER) return true
            i += 2
        }
        return false
    }

    private fun millis(pcmBytes: Long) = pcmBytes * 1000 / (sampleRate * channels * 2)

    fun close() {
        configTimer?.interrupt()
        configTimer = null
        stop()
        ready = false
        onClose(this)
    }

    companion object {
        const val MSG_FOCUS_REQUEST = 18
        const val MSG_FOCUS_RESPONSE = 19
        private const val MSG_CHANNEL_OPEN_RESPONSE = 8
        private const val MSG_DATA = 0
        private const val MSG_SETUP = 32768
        private const val MSG_START = 32769
        private const val MSG_STOP = 32770
        private const val MSG_CONFIG = 32771
        private const val MSG_ACK = 32772
        private const val MSG_SETUP_ANSWER = 255
        private const val STATUS_READY = 2
        private const val FOCUS_GAIN = 1
        private const val FOCUS_RELEASE = 4
        private const val FOCUS_STATE_GAIN = 1
        private const val FOCUS_STATE_GAIN_TRANSIENT = 2
        private const val FOCUS_STATE_GAIN_MEDIA_ONLY = 6
        private const val FOCUS_STATE_LOSS = 3
        private const val FOCUS_STATE_LOSS_TRANSIENT = 5
        private const val FOCUS_STATE_LOSS_TRANSIENT_CAN_DUCK = 4
        private const val FOCUS_STATE_GAIN_TRANSIENT_GUIDANCE_ONLY = 7
        /** Quiet long enough to be a pause, not a gap between songs, before sound counts as playing again. */
        private const val QUIET_MS = 1_500L
        /** Sound long enough not to be a click. */
        private const val SOUND_MS = 300L
        /** About -54 dB: below it, a sample is silence or noise. */
        private const val WHISPER = 64
        private const val SESSION_ID = 2
        private const val STALL_MS = 2_000L
        private const val CHUNK_LOG_EVERY = 200 // about every four seconds
        private const val CONFIG_WAIT_MS = 1_000L

        // AudioFocusStateNotification.AudioFocusState in aasdk.
        private val FOCUS_STATES = mapOf(
            1 to "GAIN", 2 to "GAIN_TRANSIENT", 3 to "LOSS", 4 to "LOSS_TRANSIENT_CAN_DUCK",
            5 to "LOSS_TRANSIENT", 6 to "GAIN_MEDIA_ONLY", 7 to "GAIN_TRANSIENT_GUIDANCE_ONLY",
        )
    }
}
