package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.ServiceDiscovery
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the media sink follows the car's audio focus. A car that takes the speakers for a call, or
 * for the phone's assistant over Bluetooth, gives them back with GAIN; myCar used to let go of its
 * focus there, and the music never came back for the rest of the drive.
 */
class AudioLinkFocusTest {
    /** What myCar sent, in order: "start", "stop" and "data" on the sink, "focus:<type>" to the car. */
    private val sent = mutableListOf<String>()
    private var takenBack = 0

    private fun link(): AudioLink = AudioLink(
        ServiceDiscovery.AudioService(
            5, ServiceDiscovery.STREAM_MEDIA, ServiceDiscovery.CODEC_PCM,
            listOf(ServiceDiscovery.AudioConfig(48_000, 16, 2)),
        ),
        sendOnChannel = { id, _ -> sent += SINK_MESSAGES[id] ?: "message $id" },
        sendControl = { _, body -> sent += "focus:${Protobuf.readInt32Field(body, 1)}" },
        onReady = {},
        onClose = {},
        onTakenBack = { takenBack++ },
    ).apply {
        onMessage(CHANNEL_OPEN_RESPONSE, Protobuf.varintField(1, 0))
        onMessage(CONFIG, Protobuf.varintField(1, READY) + Protobuf.varintField(2, 4) + Protobuf.varintField(3, 0))
        sent.clear()
    }

    /** The link streaming, the car having granted it the speakers. */
    private fun streaming(): AudioLink = link().apply {
        start()
        onFocus(GAIN)
        sent.clear()
    }

    /** [ms] of capture: silence, or a tone well above a whisper. */
    private fun AudioLink.play(ms: Int, loud: Boolean) {
        val chunk = ByteArray(CHUNK)
        if (loud) for (i in chunk.indices step 2) chunk[i + 1] = 0x20
        repeat(ms * BYTES_PER_MS / CHUNK + 1) { write(chunk, chunk.size) }
    }

    @Test
    fun `a call holds the music and the car's GAIN brings it back`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT)
        assertEquals(listOf("stop"), sent)
        link.onFocus(GAIN)
        assertEquals(listOf("stop", "start"), sent)
        assertEquals(0, takenBack)
    }

    @Test
    fun `ducking keeps the music playing`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT_CAN_DUCK)
        link.play(10, loud = true)
        assertEquals(listOf("data"), sent)
    }

    @Test
    fun `held back, a restarted capture waits for the car`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT)
        sent.clear()
        link.start()
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `the capture ending while held back gives the focus back`() {
        val link = streaming()
        link.onFocus(LOSS_TRANSIENT)
        sent.clear()
        link.stop()
        assertEquals(listOf("focus:$RELEASE"), sent)
    }

    @Test
    fun `the radio lets go and pauses the phone, and playing again asks the car back`() {
        val link = streaming()
        link.onFocus(LOSS)
        assertEquals(listOf("stop", "focus:$RELEASE"), sent)
        assertEquals(1, takenBack)
        sent.clear()
        link.play(1_600, loud = false)
        link.play(100, loud = true)
        assertEquals(emptyList<String>(), sent)
        link.play(300, loud = true)
        assertEquals(listOf("focus:$GAIN_REQUEST"), sent)
        link.onFocus(GAIN)
        assertEquals(listOf("focus:$GAIN_REQUEST", "start"), sent)
    }

    @Test
    fun `music that never paused doesn't pull the car back from the radio`() {
        val link = streaming()
        link.onFocus(LOSS)
        sent.clear()
        link.play(3_000, loud = true)
        assertEquals(emptyList<String>(), sent)
    }

    private companion object {
        const val CHANNEL_OPEN_RESPONSE = 8
        const val CONFIG = 32771
        const val READY = 2L
        val SINK_MESSAGES = mapOf(0 to "data", 32768 to "setup", 32769 to "start", 32770 to "stop")

        // AudioFocusStateNotification states, and the AudioFocusRequest types myCar sends.
        const val GAIN = 1
        const val LOSS = 3
        const val LOSS_TRANSIENT_CAN_DUCK = 4
        const val LOSS_TRANSIENT = 5
        const val GAIN_REQUEST = 1
        const val RELEASE = 4

        const val CHUNK = 4096
        const val BYTES_PER_MS = 48 * 2 * 2
    }
}
