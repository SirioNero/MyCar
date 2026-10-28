package io.github.sirionero.mycar

import io.github.sirionero.mycar.VideoSource.Companion.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The H.264 level the encoder declares to the car, by screen and frame rate (see VideoSource.levelFor). */
class VideoLevelTest {

    private fun level(width: Int, height: Int, fps: Int) = VideoSource.levelFor(width, height, fps)

    @Test
    fun `screens up to 1080p keep the levels they always had`() {
        assertEquals(Level.L31, level(800, 480, 30))
        assertEquals(Level.L31, level(800, 480, 60))
        assertEquals(Level.L31, level(1280, 720, 30))
        assertEquals(Level.L32, level(1280, 720, 60))
        assertEquals(Level.L4, level(1920, 1080, 30))
        assertEquals(Level.L42, level(1920, 1080, 60))
        assertEquals(Level.L4, level(1080, 1920, 30))
    }

    @Test
    fun `bigger screens get a level whose frames are that big`() {
        assertEquals(Level.L5, level(2560, 1440, 30))
        assertEquals(Level.L51, level(2560, 1440, 60))
        assertEquals(Level.L51, level(3840, 2160, 30))
        assertEquals(Level.L52, level(3840, 2160, 60))
    }

    @Test
    fun `the bitrate stays within the level and myCar's own cap`() {
        for ((w, h) in listOf(800 to 480, 1280 to 720, 1920 to 1080, 2560 to 1440, 3840 to 2160)) {
            for (fps in listOf(30, 60)) {
                val l = level(w, h, fps)
                val rate = VideoSource.defaultBitRate(w, h, fps, l)
                assertTrue("$w x $h @ $fps: $rate", rate <= l.maxBitRate && rate <= VideoSource.MAX_BIT_RATE)
            }
        }
    }
}
