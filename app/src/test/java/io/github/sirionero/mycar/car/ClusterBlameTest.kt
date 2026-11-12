package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.ClusterNext.NAVIGATING
import io.github.sirionero.mycar.car.ClusterNext.NOT_NAVIGATING
import io.github.sirionero.mycar.car.ClusterNext.REROUTING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Whether a car that ended the session did so over what its cluster had just been sent. */
class ClusterBlameTest {

    /** A navigation opened as ClusterNav paces it for the MG: status at 0, turn at 1 s, distance at 2 s. */
    private fun opened(): ClusterBlame = ClusterBlame().apply {
        status(NOT_NAVIGATING, -30_000)
        status(NAVIGATING, 0)
        turn(1_000)
        distance(2_000)
    }

    @Test
    fun `the MG hung up half a second after the first turn`() {
        val blame = ClusterBlame()
        blame.status(NOT_NAVIGATING, -30_000)
        blame.status(NAVIGATING, 0)
        blame.turn(1_000)
        assertEquals(550L, blame.blame(1_550, null))
    }

    @Test
    fun `up to 3 s after the navigation's last opening message`() {
        assertEquals(3_000L, opened().blame(5_000, null))
        assertNull(opened().blame(5_001, null))
    }

    @Test
    fun `a ByeBye that says someone chose to end isn't the cluster's doing`() {
        assertNull(opened().blame(2_500, 7)) // POWER_DOWN
        assertNull(opened().blame(2_500, 1)) // USER_SELECTION
        assertEquals(500L, opened().blame(2_500, 3)) // NOT_SUPPORTED
        assertEquals(500L, opened().blame(2_500, 0)) // no reason given
    }

    @Test
    fun `a later turn doesn't count, a Wi-Fi drop then isn't the cluster's`() {
        val blame = opened()
        blame.turn(60_000)
        blame.distance(61_000)
        assertNull(blame.blame(60_500, null))
    }

    @Test
    fun `a new navigation counts again`() {
        val blame = opened()
        blame.status(NOT_NAVIGATING, 100_000)
        assertNull(blame.blame(101_000, null))
        blame.status(NAVIGATING, 200_000)
        blame.turn(201_000)
        assertEquals(400L, blame.blame(201_400, null))
    }

    @Test
    fun `rerouting doesn't open a navigation again`() {
        val blame = opened()
        blame.status(REROUTING, 50_000)
        blame.status(NAVIGATING, 52_000)
        blame.turn(53_000)
        assertNull(blame.blame(53_500, null))
    }

    @Test
    fun `a navigation that starts while rerouting opens one too`() {
        val blame = ClusterBlame()
        blame.status(REROUTING, 0)
        assertEquals(1_000L, blame.blame(1_000, null))
    }

    @Test
    fun `only "not navigating" sent, nothing to blame`() {
        val blame = ClusterBlame()
        blame.status(NOT_NAVIGATING, 0)
        assertNull(blame.blame(500, null))
        blame.turn(600)
        assertNull(blame.blame(700, null))
    }
}
