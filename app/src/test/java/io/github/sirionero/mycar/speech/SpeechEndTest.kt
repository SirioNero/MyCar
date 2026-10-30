package io.github.sirionero.mycar.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SpeechEndTest {

    private val random = Random(7)

    /** 100 ms of the car's noise at [level], and of a voice [louder] times over it. */
    private fun noise(level: Float) = FloatArray(1600) { (random.nextFloat() * 2 - 1) * level * 1.7f }
    private fun voice(level: Float, louder: Float) =
        FloatArray(1600) { i -> (sin(2 * PI * 180 * i / 16000).toFloat() * level * louder * 1.4f) + (random.nextFloat() * 2 - 1) * level }

    private fun SpeechEnd.feed(pieces: Int, piece: () -> FloatArray) = repeat(pieces) { add(piece(), 1600, 100) }

    @Test
    fun `quiet after a voice, and not while it goes on`() {
        val end = SpeechEnd()
        end.feed(5) { noise(0.01f) }
        assertFalse(end.spoke)
        end.feed(20) { voice(0.01f, 6f) }
        assertTrue(end.spoke)
        assertEquals(0, end.quietMs)
        end.feed(6) { noise(0.01f) }
        assertTrue(end.quietMs >= 500)
    }

    @Test
    fun `a short pause between words isn't the end`() {
        val end = SpeechEnd()
        end.feed(5) { noise(0.02f) }
        end.feed(8) { voice(0.02f, 5f) }
        end.feed(3) { noise(0.02f) }
        val pause = end.quietMs
        end.feed(8) { voice(0.02f, 5f) }
        assertTrue(pause in 200..400)
        assertEquals(0, end.quietMs)
    }

    @Test
    fun `a car microphone that starts with zeros still finds its noise`() {
        val end = SpeechEnd()
        end.feed(4) { FloatArray(1600) }
        end.feed(3) { noise(0.01f) }
        end.feed(10) { voice(0.01f, 6f) }
        assertEquals(0, end.quietMs)
        end.feed(6) { noise(0.01f) }
        assertTrue(end.spoke)
        assertTrue(end.quietMs >= 500)
    }

    @Test
    fun `a bump is not someone speaking`() {
        val end = SpeechEnd()
        end.feed(5) { noise(0.01f) }
        end.feed(1) { voice(0.01f, 8f) }
        end.feed(5) { noise(0.01f) }
        assertFalse(end.spoke)
    }

    @Test
    fun `bumps here and there are not someone speaking`() {
        val end = SpeechEnd()
        end.feed(5) { noise(0.01f) }
        repeat(4) {
            end.feed(1) { voice(0.01f, 8f) }
            end.feed(2) { noise(0.01f) }
        }
        assertFalse(end.spoke)
    }

    @Test
    fun `the cue's tail and a microphone fading in don't count`() {
        val end = SpeechEnd()
        end.feed(1) { voice(0.01f, 8f) }
        end.feed(2) { noise(0.0003f) }
        end.feed(4) { noise(0.01f) }
        assertFalse(end.spoke)
        assertTrue(end.quietMs >= 300)
        end.feed(10) { voice(0.01f, 6f) }
        end.feed(6) { noise(0.01f) }
        assertTrue(end.spoke)
        assertTrue(end.quietMs >= 500)
    }
}
