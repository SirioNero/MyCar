package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.ServiceDiscovery
import java.util.Collections
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What goes down the media sink: one Start, the AAC configuration where the car needs it, a lead after a gap. */
class AudioLinkStreamTest {
    private class Sent(val id: Int, val body: ByteArray)

    private val sent: MutableList<Sent> = Collections.synchronizedList(mutableListOf())

    private fun link(codec: Int = ServiceDiscovery.CODEC_PCM): AudioLink = AudioLink(
        ServiceDiscovery.AudioService(
            5, ServiceDiscovery.STREAM_MEDIA, codec, listOf(ServiceDiscovery.AudioConfig(48_000, 16, 2)),
        ),
        sendOnChannel = { id, body -> sent += Sent(id, body) },
        sendControl = { _, _ -> },
        onReady = {},
        onClose = {},
    ).apply {
        onMessage(CHANNEL_OPEN_RESPONSE, Protobuf.varintField(1, 0))
        onMessage(CONFIG, Protobuf.varintField(1, READY) + Protobuf.varintField(2, 4) + Protobuf.varintField(3, 0))
        sent.clear()
    }

    private fun streaming(): AudioLink = link().apply {
        start()
        onFocus(GAIN)
        sent.clear()
    }

    private fun data() = sent.filter { it.id == DATA }

    /** Bytes of one Data message carrying [ms] of 48 kHz stereo 16-bit sound, timestamp included. */
    private fun dataSize(ms: Int) = 8 + 48_000 * ms / 1000 * 2 * 2

    @Test
    fun `the capture starting and the car's answer racing send Start once`() {
        repeat(200) {
            sent.clear()
            val link = link()
            val go = CountDownLatch(1)
            val capture = Thread { go.await(); link.start() }
            val car = Thread { go.await(); link.onFocus(GAIN) }
            capture.start()
            car.start()
            go.countDown()
            capture.join()
            car.join()
            assertEquals(1, sent.count { it.id == START })
        }
    }

    @Test
    fun `a sink that takes raw AAC gets the codec's configuration right after Start`() {
        link(ServiceDiscovery.CODEC_AAC_LC).start()
        assertEquals(listOf(START, CODEC_CONFIG), sent.map { it.id }.take(2))
        // AAC-LC, 48 kHz, two channels.
        assertArrayEquals(byteArrayOf(0x11, 0x90.toByte()), sent[1].body)
    }

    @Test
    fun `PCM and ADTS sinks get no codec configuration`() {
        for (codec in listOf(ServiceDiscovery.CODEC_PCM, ServiceDiscovery.CODEC_AAC_LC_ADTS)) {
            sent.clear()
            link(codec).start()
            assertTrue(sent.none { it.id == CODEC_CONFIG })
        }
    }

    @Test
    fun `the AudioSpecificConfig names the rate and the channels`() {
        assertArrayEquals(byteArrayOf(0x12, 0x10), AacEncoder.audioSpecificConfig(44_100, 2))
        assertArrayEquals(byteArrayOf(0x11, 0x88.toByte()), AacEncoder.audioSpecificConfig(48_000, 1))
    }

    @Test
    fun `after the car runs dry, silence goes ahead of the next chunk`() {
        val link = streaming()
        link.onMessage(UNDERFLOW, byteArrayOf())
        val chunk = ByteArray(1_920) { 1 }
        link.write(chunk, chunk.size)
        assertEquals(listOf(dataSize(60), 8 + chunk.size), data().map { it.body.size })
        assertTrue(data()[0].body.drop(8).all { it == 0.toByte() })
        // Once only: the next chunk goes as it is.
        sent.clear()
        link.write(chunk, chunk.size)
        assertEquals(listOf(8 + chunk.size), data().map { it.body.size })
    }

    @Test
    fun `the lead stops growing at its cap`() {
        val link = streaming()
        repeat(10) { link.onMessage(UNDERFLOW, byteArrayOf()) }
        link.write(ByteArray(1_920), 1_920)
        assertEquals(dataSize(240), data()[0].body.size)
    }

    @Test
    fun `a new stream starts without a lead`() {
        val link = streaming()
        link.onMessage(UNDERFLOW, byteArrayOf())
        link.onFocus(LOSS_TRANSIENT)
        link.onFocus(GAIN)
        sent.clear()
        link.write(ByteArray(1_920), 1_920)
        assertEquals(listOf(8 + 1_920), data().map { it.body.size })
    }

    @Test
    fun `a car that never acks gets its stream without a wait`() {
        val link = streaming()
        val took = kotlin.system.measureTimeMillis { repeat(20) { link.write(ByteArray(1_920), 1_920) } }
        assertEquals(20, data().size)
        assertTrue("the writer waited ${took}ms", took < 1_000)
    }

    @Test
    fun `once the car acks, a full window waits for its next ack`() {
        val link = streaming()
        link.onMessage(ACK, Protobuf.varintField(1, 0) + Protobuf.varintField(2, 1))
        repeat(4) { link.write(ByteArray(1_920), 1_920) }
        val fifth = Thread { link.write(ByteArray(1_920), 1_920) }.apply { start() }
        fifth.join(300)
        assertTrue("the fifth chunk went before the car had room", fifth.isAlive)
        link.onMessage(ACK, Protobuf.varintField(1, 0) + Protobuf.varintField(2, 1))
        fifth.join(1_000)
        assertEquals(5, data().size)
    }

    @Test
    fun `an ack quicker than the writer leaves nothing in flight`() {
        lateinit var link: AudioLink
        // The car acks each chunk at once, before send() has returned.
        link = AudioLink(
            ServiceDiscovery.AudioService(
                5, ServiceDiscovery.STREAM_MEDIA, ServiceDiscovery.CODEC_PCM, listOf(ServiceDiscovery.AudioConfig(48_000, 16, 2)),
            ),
            sendOnChannel = { id, body ->
                sent += Sent(id, body)
                if (id == DATA) link.onMessage(ACK, Protobuf.varintField(1, 0) + Protobuf.varintField(2, 1))
            },
            sendControl = { _, _ -> },
            onReady = {},
            onClose = {},
        )
        link.onMessage(CHANNEL_OPEN_RESPONSE, Protobuf.varintField(1, 0))
        link.onMessage(CONFIG, Protobuf.varintField(1, READY) + Protobuf.varintField(2, 4) + Protobuf.varintField(3, 0))
        link.start()
        link.onFocus(GAIN)
        sent.clear()
        val took = kotlin.system.measureTimeMillis { repeat(12) { link.write(ByteArray(1_920), 1_920) } }
        assertEquals(12, data().size)
        assertTrue("the window closed on acked chunks: waited ${took}ms", took < 1_000)
    }

    private companion object {
        const val ACK = 32772
        const val CHANNEL_OPEN_RESPONSE = 8
        const val CONFIG = 32771
        const val READY = 2L
        const val DATA = 0
        const val CODEC_CONFIG = 1
        const val START = 32769
        const val UNDERFLOW = 32779
        const val GAIN = 1
        const val LOSS_TRANSIENT = 5
    }
}
