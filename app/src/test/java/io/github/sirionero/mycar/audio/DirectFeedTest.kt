package io.github.sirionero.mycar.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * myCar's own music (Xonos inside myCar) going straight into the car's media sink: at the car's
 * pace, with silence between songs, never over the capture of another app, and timed so the
 * music engine knows when each frame is heard.
 */
class DirectFeedTest {
    private var now = 0L
    private var captureOn = false

    /** Every chunk handed to the car, as samples. */
    private val chunks = mutableListOf<ShortArray>()

    private val link = object : CarAudio.Link {
        override val sampleRate = RATE
        override val channels = 2
        override fun start() = Unit
        override fun stop() = Unit
        override fun write(pcm: ByteArray, size: Int) {
            chunks += ShortArray(size / 2) { i -> ((pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8)).toShort() }
        }
    }

    private fun feed() = DirectFeed(link, { captureOn }, Any(), { now })

    private fun ms(value: Long) = value * 1_000_000L

    @Test
    fun `the car gets silence at its pace while nothing plays`() {
        val feed = feed()
        feed.pump(0)
        // The lead: 60 ms ahead of the clock, in 20 ms chunks.
        assertEquals(3, chunks.size)
        // A second of the feed's loop, which wakes every few milliseconds: a second's worth more.
        for (t in 10L..1_000L step 10) {
            now = ms(t)
            feed.pump(now)
        }
        assertEquals(53, chunks.size)
        assertTrue(chunks.all { chunk -> chunk.all { it == 0.toShort() } })
        assertEquals(CHUNK_FRAMES * 2, chunks[0].size)
    }

    @Test
    fun `music written goes to the car in order, then silence`() {
        val feed = feed()
        val writer = feed.open(RATE, 2)
        val music = ShortArray(CHUNK_FRAMES * 2 * 2) { (it / 2 + 1).toShort() }
        assertEquals(CHUNK_FRAMES * 2, writer.write(music, 0, CHUNK_FRAMES * 2))
        feed.pump(0)
        assertEquals(3, chunks.size)
        assertEquals(1.toShort(), chunks[0][0])
        assertEquals(music.last(), chunks[1].last())
        assertTrue(chunks[2].all { it == 0.toShort() })
        assertEquals(CHUNK_FRAMES * 2L, writer.playedFrames)
    }

    @Test
    fun `with the capture on, a quiet feed leaves the car to it`() {
        captureOn = true
        val feed = feed()
        feed.pump(0)
        assertEquals(0, chunks.size)
        assertTrue(!feed.feeding)
        val writer = feed.open(RATE, 2)
        writer.write(ShortArray(CHUNK_FRAMES * 2) { 7 }, 0, CHUNK_FRAMES)
        now = ms(20)
        feed.pump(now)
        assertTrue(chunks.isNotEmpty())
        assertTrue(feed.feeding)
        // Two seconds after the music stops, the capture has the car again.
        now = ms(2_100)
        val before = chunks.size
        feed.pump(now)
        val after = chunks.size
        now = ms(2_200)
        feed.pump(now)
        assertTrue(!feed.feeding)
        assertEquals(after, chunks.size)
        assertTrue(after >= before)
    }

    @Test
    fun `a frame's time counts what waits before it and the car's delay`() {
        val feed = feed()
        val writer = feed.open(RATE, 2)
        writer.write(ShortArray(RATE / 10 * 2), 0, RATE / 10) // 100 ms
        assertEquals(DirectFeed.CAR_DELAY_US, writer.presentationTimeUs(0, 0))
        assertEquals(100_000L + DirectFeed.CAR_DELAY_US, writer.presentationTimeUs((RATE / 10).toLong(), 0))
        feed.pump(0) // 60 ms taken
        assertEquals(CHUNK_FRAMES * 3L, writer.playedFrames)
        assertEquals(40_000L + DirectFeed.CAR_DELAY_US, writer.presentationTimeUs((RATE / 10).toLong(), 0))
    }

    @Test
    fun `a full feed holds the writer until the car takes some`() {
        val feed = feed()
        val writer = feed.open(RATE, 2)
        val ringFrames = RATE * DirectFeed.RING_MS / 1000
        assertEquals(ringFrames, writer.write(ShortArray(ringFrames * 2), 0, ringFrames))
        val done = CountDownLatch(1)
        thread {
            writer.write(ShortArray(CHUNK_FRAMES * 2), 0, CHUNK_FRAMES)
            done.countDown()
        }
        assertTrue(!done.await(200, TimeUnit.MILLISECONDS))
        feed.pump(0)
        assertTrue(done.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `interrupting frees a held writer and drops what waits`() {
        val feed = feed()
        val writer = feed.open(RATE, 2)
        val ringFrames = RATE * DirectFeed.RING_MS / 1000
        writer.write(ShortArray(ringFrames * 2) { 5 }, 0, ringFrames)
        var result = 0
        val done = CountDownLatch(1)
        thread {
            result = writer.write(ShortArray(CHUNK_FRAMES * 2), 0, CHUNK_FRAMES)
            done.countDown()
        }
        Thread.sleep(100)
        writer.interrupt()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(-1, result)
        feed.pump(0)
        assertTrue(chunks.all { chunk -> chunk.all { it == 0.toShort() } })
    }

    @Test
    fun `mono music plays on both of the car's channels`() {
        val feed = feed()
        val writer = feed.open(RATE, 1)
        writer.write(ShortArray(CHUNK_FRAMES) { 9 }, 0, CHUNK_FRAMES)
        feed.pump(0)
        assertTrue(chunks[0].all { it == 9.toShort() })
    }

    @Test
    fun `music at another rate is converted smoothly, across writes`() {
        val feed = feed()
        val writer = feed.open(44_100, 2)
        // A slow ramp at 44.1 kHz, in uneven pieces.
        val frames = 44_100 / 10
        val ramp = ShortArray(frames * 2) { (it / 2).toShort() }
        var at = 0
        for (piece in listOf(1_000, 7, 2_000, frames - 3_007)) {
            writer.write(ramp, at, piece)
            at += piece
        }
        now = ms(100)
        feed.pump(now)
        val left = chunks.flatMap { chunk -> chunk.filterIndexed { i, _ -> i % 2 == 0 } }
        val music = left.dropLastWhile { it == 0.toShort() }
        // 100 ms of music at 48 kHz, give or take a frame.
        assertTrue(abs(music.size - RATE / 10) <= 2)
        // Rising by 44.1/48 of a step each frame, with no jump between the pieces.
        for (i in 1 until music.size) {
            val step = music[i] - music[i - 1]
            assertTrue("step $step at $i", step in 0..1)
        }
    }

    @Test
    fun `a write the car held up makes no burst, nor a long sleep after`() {
        var held = true
        val slow = object : CarAudio.Link {
            override val sampleRate = RATE
            override val channels = 2
            override fun start() = Unit
            override fun stop() = Unit
            override fun write(pcm: ByteArray, size: Int) {
                chunks += ShortArray(size / 2)
                // The first write waits half a second for the car.
                if (held) {
                    held = false
                    now += ms(500)
                }
            }
        }
        val wait = DirectFeed(slow, { captureOn }, Any(), { now }).pump(0)
        assertTrue("${chunks.size} chunks at once", chunks.size <= 3)
        assertTrue("then slept ${wait / 1_000_000} ms", wait <= ms(40))
    }

    private companion object {
        const val RATE = 48_000
        const val CHUNK_FRAMES = RATE * DirectFeed.CHUNK_MS / 1000
    }
}
