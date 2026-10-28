package io.github.sirionero.mycar.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the Plancia or Xonos app takes the place of the one inside myCar, through a drive. */
class TakeOverTest {

    private fun TakeOver.at(
        now: Long,
        configured: Boolean = true,
        appThere: Boolean = true,
        connected: Boolean = false,
        online: Boolean = true,
        outOfReach: Boolean = false,
        refused: Boolean = false,
    ) = update(now, configured, appThere, connected, online, outOfReach, refused)

    @Test
    fun `out of reach on the tailnet, the app takes over at once`() {
        assertTrue(TakeOver().at(0, outOfReach = true))
    }

    @Test
    fun `a refused token, the app takes over at once`() {
        assertTrue(TakeOver().at(0, refused = true))
    }

    @Test
    fun `connected, the one inside myCar stays`() {
        val t = TakeOver()
        assertFalse(t.at(0, connected = true))
        assertFalse(t.at(60_000, connected = true))
    }

    @Test
    fun `never connected, the app takes over after the start's grace`() {
        val t = TakeOver(startGraceMs = 10_000, dropGraceMs = 30_000)
        assertFalse(t.at(0))
        assertFalse(t.at(9_999))
        assertTrue(t.at(10_000))
    }

    @Test
    fun `a connection lost mid-drive gets the longer grace`() {
        val t = TakeOver(startGraceMs = 10_000, dropGraceMs = 30_000)
        assertFalse(t.at(0, connected = true))
        assertFalse(t.at(1_000))
        assertFalse(t.at(20_000))
        assertTrue(t.at(31_000))
    }

    @Test
    fun `back before the grace runs out, the clock starts over`() {
        val t = TakeOver(startGraceMs = 10_000, dropGraceMs = 30_000)
        assertFalse(t.at(0))
        assertFalse(t.at(8_000, connected = true))
        assertFalse(t.at(9_000))
        assertFalse(t.at(30_000))
        assertTrue(t.at(39_000))
    }

    @Test
    fun `offline time doesn't count`() {
        val t = TakeOver(startGraceMs = 10_000)
        assertFalse(t.at(0, online = false))
        assertFalse(t.at(20_000, online = false))
        assertFalse(t.at(21_000))
        assertFalse(t.at(30_000))
        assertTrue(t.at(31_000))
    }

    @Test
    fun `without the app on the phone, nothing takes over`() {
        val t = TakeOver()
        assertFalse(t.at(0, appThere = false, outOfReach = true))
        assertFalse(t.at(60_000, appThere = false))
    }

    @Test
    fun `not set up inside myCar, nothing to take over from`() {
        assertFalse(TakeOver().at(0, configured = false, outOfReach = true))
    }

    @Test
    fun `once taken over, for the rest of the drive`() {
        val t = TakeOver()
        assertTrue(t.at(0, outOfReach = true))
        assertTrue(t.at(1_000, connected = true))
    }
}
