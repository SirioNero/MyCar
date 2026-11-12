package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.ClusterNext.Message
import io.github.sirionero.mycar.car.ClusterNext.NAVIGATING
import io.github.sirionero.mycar.car.ClusterNext.NOT_NAVIGATING
import io.github.sirionero.mycar.car.ClusterNext.REROUTING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One message at a time, newest state, status before turn before distance. */
class ClusterNextTest {

    /** What the map app says at a moment: the status, the turn and the distance. */
    private data class Now(val status: Int, val turn: String? = null, val distance: String? = null)

    /**
     * The car's side of ClusterNav's loop: one look every 500 ms when there's nothing to send, the
     * car's interval after a message. [at] says what the map app says at each moment.
     */
    private fun run(intervalMs: Long, untilMs: Long, at: (Long) -> Now): List<Pair<Long, Message>> {
        val sent = mutableListOf<Pair<Long, Message>>()
        var status = -1
        var turn: String? = null
        var distance: String? = null
        var t = 0L
        while (t <= untilMs) {
            val now = at(t)
            val next = ClusterNext.of(now.status, status, now.turn, turn, now.distance, distance)
            when (next) {
                Message.STATUS -> {
                    status = now.status
                    if (status == NOT_NAVIGATING) { turn = null; distance = null }
                }
                Message.TURN -> turn = now.turn
                Message.DISTANCE -> distance = now.distance
                null -> {}
            }
            next?.let { sent += t to it }
            t += if (next != null) intervalMs else 500
        }
        return sent
    }

    @Test
    fun `the MG's burst becomes three messages a second apart`() {
        // Navigation starts at 0: before, status, turn and distance went out in the same millisecond.
        val sent = run(1000, 2500) { Now(NAVIGATING, "depart", "300 m") }
        assertEquals(listOf(0L to Message.STATUS, 1000L to Message.TURN, 2000L to Message.DISTANCE), sent)
    }

    @Test
    fun `a car that asks for nothing gets them half a second apart, as myCar always looked`() {
        val sent = run(500, 1200) { Now(NAVIGATING, "depart", "300 m") }
        assertEquals(listOf(0L to Message.STATUS, 500L to Message.TURN, 1000L to Message.DISTANCE), sent)
    }

    @Test
    fun `a new turn goes before a newer distance`() {
        assertEquals(Message.TURN, ClusterNext.of(NAVIGATING, NAVIGATING, "turn right", "depart", "50 m", "300 m"))
    }

    @Test
    fun `the distance goes with the newest number only`() {
        // The distance changes every look; the car gets the newest at each interval, never a queue.
        val sent = run(1000, 4000) { t -> Now(NAVIGATING, "depart", "${1000 - t / 100} m") }
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L), sent.map { it.first })
        assertEquals(Message.DISTANCE, sent.last().second)
    }

    @Test
    fun `nothing new, nothing sent`() {
        assertNull(ClusterNext.of(NAVIGATING, NAVIGATING, "depart", "depart", "300 m", "300 m"))
        assertNull(ClusterNext.of(NOT_NAVIGATING, NOT_NAVIGATING, null, null, null, null))
    }

    @Test
    fun `while rerouting, no turn and no distance`() {
        assertEquals(Message.STATUS, ClusterNext.of(REROUTING, NAVIGATING, "depart", "depart", "300 m", "300 m"))
        assertNull(ClusterNext.of(REROUTING, REROUTING, "turn left", "depart", "20 m", "300 m"))
    }

    @Test
    fun `at the start "not navigating" goes out once, and nothing after`() {
        val sent = run(1000, 5000) { Now(NOT_NAVIGATING) }
        assertEquals(listOf(0L to Message.STATUS), sent)
    }

    @Test
    fun `a turn waits for the status that says navigating`() {
        assertEquals(Message.STATUS, ClusterNext.of(NAVIGATING, NOT_NAVIGATING, "depart", null, "300 m", null))
    }
}
