package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.MyCarLog
import java.util.concurrent.locks.LockSupport
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * myCar's own music into the car's media sink, the way the capture feeds it other apps' sound but
 * with no capture and no consent question: Xonos inside myCar writes its PCM to a [Writer], and a
 * thread hands it to the car 20 ms at a time, at the pace the car plays it.
 *
 * While there's none - a pause, the gap between songs - the thread sends silence, as a capture of
 * a quiet phone would, so the car keeps its speakers for the phone and the stream never stalls.
 * With the capture on as well (another app's sound), the capture writes instead while myCar's
 * own music is quiet: only one of them feeds the car at a time ([feeding]).
 */
internal class DirectFeed(
    val link: CarAudio.Link,
    /** True while the capture is feeding the car: a quiet feed leaves the car to it. */
    private val captureOn: () -> Boolean,
    /** Held for each write to the car, as the capture holds it: never both at once (an AAC encoder can't take it). */
    private val writes: Any = Any(),
    private val nanos: () -> Long = System::nanoTime,
) {
    private val rate = link.sampleRate
    private val channels = link.channels
    private val lock = Object()

    // The music waiting for the car, in the car's format: a ring of interleaved samples.
    private val ring = ShortArray(rate * RING_MS / 1000 * channels)
    private var head = 0
    private var queued = 0

    /** Frames the car has taken from the ring for the current writer. */
    private var taken = 0L
    private var hadMusic = false
    private var lastMusicAt = 0L
    private var writer: Writer? = null

    // The pace: frames handed to the car since [anchor], always [leadFrames] ahead of the clock.
    private val leadFrames = rate.toLong() * LEAD_MS / 1000
    private val maxBehindFrames = rate.toLong() * MAX_BEHIND_MS / 1000
    private val chunkFrames = rate * CHUNK_MS / 1000
    private val chunk = ShortArray(chunkFrames * channels)
    private val bytes = ByteArray(chunk.size * 2)
    private var anchor = nanos()
    private var sent = 0L

    @Volatile private var running = false
    private var thread: Thread? = null

    /** True while myCar's own music is reaching the car, and for a moment after: the capture waits. */
    val feeding: Boolean
        get() = synchronized(lock) { queued > 0 || (hadMusic && nanos() - lastMusicAt < TAIL_NS) }

    fun start() {
        if (running) return
        running = true
        anchor = nanos()
        sent = 0
        thread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (running) {
                val wait = try {
                    pump(nanos())
                } catch (e: Exception) {
                    MyCarLog.e("audio: myCar's own music stopped reaching the car", e)
                    CHUNK_MS * 1_000_000L
                }
                if (running) LockSupport.parkNanos(wait)
            }
        }, "mycar-direct-audio").apply {
            isDaemon = true
            start()
        }
        MyCarLog.i("audio: myCar's own music goes straight to the car (${rate}Hz x$channels)")
    }

    fun stop() {
        running = false
        thread?.let(LockSupport::unpark)
        thread = null
        synchronized(lock) {
            writer?.closed = true
            writer = null
            queued = 0
            lock.notifyAll()
        }
    }

    /**
     * Hands the car every chunk that's due by [now] (music, or silence when there's none) and says
     * how long until the next one, in nanoseconds. The feed thread's loop; called directly in tests.
     */
    fun pump(now: Long): Long {
        var due = (now - anchor) * rate / 1_000_000_000L + leadFrames
        if (due - sent > maxBehindFrames) {
            // The car held the stream up (its acks stopped for a while): carry on from now
            // rather than send all that's late in one go.
            sent = due - chunkFrames
        }
        while (sent < due) {
            val music = take()
            if (music > 0 || !captureOn() || feeding) {
                toBytes()
                synchronized(writes) { link.write(bytes, bytes.size) }
            }
            sent += chunkFrames
            due = (nanos() - anchor) * rate / 1_000_000_000L + leadFrames
            // A write the car held up (waiting for its acks) made all that came due meanwhile late:
            // on from now, as above, rather than in one burst the car would partly drop.
            if (due - sent > maxBehindFrames) sent = due - chunkFrames
        }
        val nextAt = anchor + (sent + 1 - leadFrames) * 1_000_000_000L / rate
        // From the clock as it is now: after a held-up write, [now] is long gone, and the thread
        // slept as long again with the car's buffer running dry.
        return max(MIN_WAIT_NS, nextAt - nanos())
    }

    /** One chunk out of the ring, silence where it runs short; how many frames were music. */
    private fun take(): Int = synchronized(lock) {
        val frames = min(queued / channels, chunkFrames)
        val samples = frames * channels
        val first = min(samples, ring.size - head)
        System.arraycopy(ring, head, chunk, 0, first)
        if (samples > first) System.arraycopy(ring, 0, chunk, first, samples - first)
        chunk.fill(0, samples, chunk.size)
        head = (head + samples) % ring.size
        queued -= samples
        taken += frames
        if (frames > 0) {
            hadMusic = true
            lastMusicAt = nanos()
            lock.notifyAll()
        }
        frames
    }

    private fun toBytes() {
        for (i in chunk.indices) {
            val s = chunk[i].toInt()
            bytes[i * 2] = s.toByte()
            bytes[i * 2 + 1] = (s shr 8).toByte()
        }
    }

    /** A new writer for music at [sampleRate] x [channelCount]; the one before it, if any, is closed. */
    fun open(sampleRate: Int, channelCount: Int): Writer = synchronized(lock) {
        writer?.closed = true
        queued = 0
        taken = 0
        lock.notifyAll()
        Writer(sampleRate, channelCount).also { writer = it }
    }

    /**
     * Where one stream of music goes: frames in its own rate and channels, turned into the car's
     * as they come, with the volume applied. [write] blocks while the car has enough waiting.
     */
    inner class Writer(val sampleRate: Int, val channelCount: Int) {
        @Volatile var gain = 1f
        @Volatile internal var closed = false

        // Rate conversion, only for a car that doesn't take the music's rate (most take 48 kHz).
        private val step = sampleRate.toDouble() / rate
        private var position = 0.0
        private var previous = ShortArray(channelCount)
        private var converted = ShortArray(0)

        /** Frames of this writer's music the car has taken so far. */
        val playedFrames: Long
            get() = synchronized(lock) { if (writer === this) taken * sampleRate / rate else 0L }

        /** When the car will play frame number [frame] of this writer's music, on [nowUs]'s clock. */
        fun presentationTimeUs(frame: Long, nowUs: Long): Long =
            nowUs + (frame - playedFrames) * 1_000_000L / sampleRate + CAR_DELAY_US

        /** Writes [frames] frames from [offsetFrames]; blocks while there's no room. -1 once interrupted or closed. */
        fun write(data: ShortArray, offsetFrames: Int, frames: Int): Int {
            if (closed) return -1
            val out = convert(data, offsetFrames, frames)
            var done = 0
            while (done < out) {
                synchronized(lock) {
                    while (!closed && writer === this && queued == ring.size) lock.wait(WRITE_WAIT_MS)
                    if (closed || writer !== this) return -1
                    val samples = min(out - done, ring.size - queued)
                    val tail = (head + queued) % ring.size
                    val first = min(samples, ring.size - tail)
                    System.arraycopy(converted, done, ring, tail, first)
                    if (samples > first) System.arraycopy(converted, done + first, ring, 0, samples - first)
                    queued += samples
                    done += samples
                }
            }
            return frames
        }

        /** Stops at once and drops what's waiting; this writer takes no more. Any thread. */
        fun interrupt() {
            synchronized(lock) {
                if (writer === this) {
                    queued = 0
                    taken = 0
                }
                closed = true
                lock.notifyAll()
            }
        }

        /** Done: what's still waiting is dropped, as an AudioTrack let go of stops at once. */
        fun close() {
            synchronized(lock) {
                closed = true
                if (writer === this) {
                    writer = null
                    queued = 0
                }
                lock.notifyAll()
            }
        }

        /** The frames in the car's format and volume, into [converted]; how many samples. */
        private fun convert(data: ShortArray, offsetFrames: Int, frames: Int): Int {
            val g = gain
            if (sampleRate == rate) {
                val samples = frames * channels
                if (converted.size < samples) converted = ShortArray(samples)
                for (f in 0 until frames) {
                    for (c in 0 until channels) converted[f * channels + c] = scaled(sample(data, offsetFrames + f, c), g)
                }
                return samples
            }
            if (frames <= 0) return 0
            // Linear interpolation between input frames; [position] is where the next car frame
            // falls, in input frames from this block's start (-1 is the last frame of the block before).
            val count = ceil((frames - 1 - position) / step).toInt().coerceAtLeast(0)
            val samples = count * channels
            if (converted.size < samples) converted = ShortArray(samples)
            for (k in 0 until count) {
                val at = position + k * step
                val base = floor(at).toInt()
                val frac = at - base
                for (c in 0 until channels) {
                    val a = if (base < 0) previousSample(c) else sample(data, offsetFrames + base, c)
                    val b = sample(data, offsetFrames + base + 1, c)
                    converted[k * channels + c] = scaled((a + (b - a) * frac).roundToInt(), g)
                }
            }
            position += count * step - frames
            previous = ShortArray(channelCount) { c -> data[(offsetFrames + frames - 1) * channelCount + c] }
            return samples
        }

        private fun previousSample(c: Int): Int = when {
            channelCount == channels -> previous[c].toInt()
            channelCount == 1 -> previous[0].toInt()
            channels == 1 -> (previous[0].toInt() + previous[1].toInt()) / 2
            else -> previous[min(c, channelCount - 1)].toInt()
        }

        /** Channel [c] of the car's at [frame], from music in mono or with other channels. */
        private fun sample(data: ShortArray, frame: Int, c: Int): Int = when {
            channelCount == channels -> data[frame * channelCount + c].toInt()
            channelCount == 1 -> data[frame].toInt()
            channels == 1 -> (data[frame * channelCount].toInt() + data[frame * channelCount + 1].toInt()) / 2
            else -> data[frame * channelCount + min(c, channelCount - 1)].toInt()
        }

        private fun scaled(sample: Int, g: Float): Short =
            if (g >= 1f) sample.coerceIn(-32768, 32767).toShort()
            else (sample * g).roundToInt().coerceIn(-32768, 32767).toShort()
    }

    companion object {
        const val CHUNK_MS = 20

        /** How much music waits for the car: what [Writer.write] fills before it blocks. */
        const val RING_MS = 250

        /** How far ahead of the clock the car is fed, against the phone's own hiccups. */
        const val LEAD_MS = 60

        /** The car's own delay from a chunk's arrival to its sound: a guess, as cars don't say. */
        private const val CAR_EXTRA_DELAY_MS = 100

        /** From a frame leaving the ring to the car playing it: the lead, then the car's delay. */
        const val CAR_DELAY_US = (LEAD_MS + CAR_EXTRA_DELAY_MS) * 1_000L

        /** Further behind than this, the feed carries on from now instead of catching up. */
        private const val MAX_BEHIND_MS = 200

        /** After the music stops, the capture waits this long before it feeds the car again. */
        private const val TAIL_NS = 2_000_000_000L
        private const val MIN_WAIT_NS = 1_000_000L
        private const val WRITE_WAIT_MS = 50L
    }
}
