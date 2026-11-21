package io.github.sirionero.mycar.speech

import io.github.sirionero.mycar.speech.SpeechRoom.Press
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the model that rereads the phrase loads, from drive logs of a Pixel 10 Pro (see SpeechRoom). */
class SpeechRoomTest {

    private fun mb(n: Long) = n shl 20

    /** Parakeet multilingual: 640 MB on disk, 5/4 of it in memory. */
    private val parakeet = mb(799)
    private val line = mb(216)

    @Test
    fun `ahead of a command only with plenty of room`() {
        assertTrue(SpeechRoom.ahead(mb(4_116), line, low = false, need = parakeet))
        // 6 November, 18:03: 2546 MB free - it waited for the first command.
        assertFalse(SpeechRoom.ahead(mb(2_546), line, low = false, need = parakeet))
        assertFalse(SpeechRoom.ahead(mb(9_000), line, low = true, need = parakeet))
    }

    @Test
    fun `at a press with plenty of room, as always`() {
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(4_000), line, low = false, need = parakeet, english = true))
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(4_000), line, low = false, need = parakeet, english = false))
    }

    @Test
    fun `Italian at a press still loads under the margin - the only model that hears it`() {
        // 6 November, 18:04: 2497 MB free, Parakeet loaded at the press and heard the command.
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(2_497), line, low = false, need = parakeet, english = false))
        // A gigabyte left after it.
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(1_900), line, low = false, need = parakeet, english = false))
    }

    @Test
    fun `Italian at a press on the memory of the evenings like those of the crashes - nothing loads`() {
        // 7 November, evening: 1493 and 1221 MB free as Kroko loaded, 1647 and 1506 MB a moment after.
        for (free in listOf(1_647L, 1_506L, 1_493L, 1_221L)) {
            assertEquals("$free MB free", Press.NONE, SpeechRoom.atPress(mb(free), line, low = false, need = parakeet, english = false))
        }
    }

    @Test
    fun `a phone whose line is high - twice the line left after the model, not just the gigabyte`() {
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(2_497), mb(700), low = false, need = parakeet, english = false))
        assertEquals(Press.NONE, SpeechRoom.atPress(mb(2_100), mb(700), low = false, need = parakeet, english = false))
    }

    @Test
    fun `English under the margin keeps the live words`() {
        assertEquals(Press.LIVE_WORDS, SpeechRoom.atPress(mb(2_497), line, low = false, need = parakeet, english = true))
        assertEquals(Press.LIVE_WORDS, SpeechRoom.atPress(mb(600), line, low = true, need = parakeet, english = true))
    }

    @Test
    fun `another language with the phone short of memory - nothing loads`() {
        assertEquals(Press.NONE, SpeechRoom.atPress(mb(900), line, low = false, need = parakeet, english = false))
        assertEquals(Press.NONE, SpeechRoom.atPress(mb(3_000), line, low = true, need = parakeet, english = false))
    }

    /** Canary: 198 MB on disk, 5/4 of it in memory. */
    private val canary = mb(248)

    /** Whisper small: 358 MB on disk - not a small model. */
    private val whisper = mb(447)

    @Test
    fun `Canary on a Pixel 8 - loads with the free memory of the 17 November log`() {
        // 17:28: 1522 MB free; 17:32: 914 MB free. Both stayed out under the margins sized on Parakeet.
        for (free in listOf(1_522L, 914L)) {
            assertTrue("$free MB free", SpeechRoom.ahead(mb(free), line, low = false, need = canary))
            assertEquals("$free MB free", Press.LOAD, SpeechRoom.atPress(mb(free), line, low = false, need = canary, english = true))
        }
    }

    @Test
    fun `Canary - still twice Android's line left after it`() {
        // 914 MB free with a 400 MB line: 666 MB left, under 800.
        assertEquals(Press.LIVE_WORDS, SpeechRoom.atPress(mb(914), mb(400), low = false, need = canary, english = true))
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(1_522), mb(400), low = false, need = canary, english = true))
    }

    @Test
    fun `Canary - never with too little left, nor with the phone already low`() {
        // 700 MB free: 452 MB left, under the 512 MB floor.
        assertEquals(Press.LIVE_WORDS, SpeechRoom.atPress(mb(700), line, low = false, need = canary, english = true))
        assertEquals(Press.NONE, SpeechRoom.atPress(mb(700), line, low = false, need = canary, english = false))
        assertEquals(Press.LIVE_WORDS, SpeechRoom.atPress(mb(3_000), line, low = true, need = canary, english = true))
    }

    @Test
    fun `bigger models keep the margins sized on Parakeet`() {
        assertFalse(SpeechRoom.ahead(mb(1_522), line, low = false, need = whisper))
        assertFalse(SpeechRoom.ahead(mb(1_522), line, low = false, need = parakeet))
        assertEquals(Press.LIVE_WORDS, SpeechRoom.atPress(mb(1_522), line, low = false, need = parakeet, english = true))
    }
}
