package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What myCar keeps of a car's cluster from drive to drive: hang-ups in a row, a navigation shown. */
class ClusterRecordTest {

    @Test
    fun `one hang-up takes nothing, two in a row take the turns`() {
        val once = ClusterRecord().hungUp()
        assertEquals(1, once.strikes)
        assertFalse(once.off)
        val twice = once.hungUp()
        assertEquals(2, twice.strikes)
        assertTrue(twice.off)
    }

    @Test
    fun `a Wi-Fi drop, then a navigation that goes through, then another drop takes nothing`() {
        val record = ClusterRecord().hungUp().tookIt().hungUp()
        assertEquals(1, record.strikes)
        assertFalse(record.off)
    }

    @Test
    fun `a car that crashes at every opening loses its turns at the second, reconnection or not`() {
        // A first destination, then the route picked up again after the reconnection: two in a row.
        assertTrue(ClusterRecord().hungUp().hungUp().off)
    }

    @Test
    fun `a car that has shown a navigation is never blamed`() {
        val record = ClusterRecord().hungUp().showedIt()
        assertEquals(0, record.strikes)
        assertTrue(record.held)
        val after = record.hungUp().hungUp().hungUp()
        assertFalse(after.off)
        assertEquals(0, after.strikes)
    }

    @Test
    fun `turns again start the count from nothing`() {
        val off = ClusterRecord().hungUp().hungUp()
        val again = off.turnsAgain()
        assertFalse(again.off)
        assertEquals(0, again.strikes)
        assertFalse(again.hungUp().off)
        assertTrue(again.hungUp().hungUp().off)
    }

    @Test
    fun `a car without turns stays so until the driver says`() {
        val off = ClusterRecord().hungUp().hungUp()
        assertEquals(off, off.hungUp())
        assertEquals(off.copy(strikes = 0), off.tookIt())
        assertTrue(off.tookIt().off)
    }
}
