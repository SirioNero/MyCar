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
     * car's interval after a round. [oneAtATime]: a cluster that wants pictures. [at] says what the map
     * app says at each moment.
     */
    private fun run(intervalMs: Long, untilMs: Long, oneAtATime: Boolean = true, at: (Long) -> Now): List<Pair<Long, Message>> {
        val sent = mutableListOf<Pair<Long, Message>>()
        var status = -1
        var turn: String? = null
        var distance: String? = null
        var last: Message? = null
        var t = 0L
        while (t <= untilMs) {
            val now = at(t)
            val round = if (oneAtATime) {
                listOfNotNull(ClusterNext.of(now.status, status, now.turn, turn, now.distance, distance, last))
            } else {
                ClusterNext.owed(now.status, status, now.turn, turn, now.distance, distance)
            }
            for (message in round) {
                when (message) {
                    Message.STATUS -> {
                        status = now.status
                        if (status == NOT_NAVIGATING) { turn = null; distance = null }
                    }
                    Message.TURN -> turn = now.turn
                    Message.DISTANCE -> distance = now.distance
                }
                sent += t to message
                last = message
            }
            t += if (round.isNotEmpty()) intervalMs else 500
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
    fun `a car that takes codes gets status, turn and distance together, as before`() {
        val sent = run(1000, 3000, oneAtATime = false) { Now(NAVIGATING, "depart", "300 m") }
        assertEquals(listOf(0L to Message.STATUS, 0L to Message.TURN, 0L to Message.DISTANCE), sent)
    }

    @Test
    fun `a car that takes codes gets one round per interval, with the newest of everything`() {
        // The distance changes every 100 ms, the turn at 1.5 s: rounds at 0, 1 s, 2 s... carry only the newest.
        val sent = run(1000, 2500, oneAtATime = false) { t ->
            Now(NAVIGATING, if (t < 1500) "depart" else "turn right", "${1000 - t / 100} m")
        }
        assertEquals(
            listOf(
                0L to Message.STATUS, 0L to Message.TURN, 0L to Message.DISTANCE,
                1000L to Message.DISTANCE,
                2000L to Message.TURN, 2000L to Message.DISTANCE,
            ),
            sent,
        )
    }

    @Test
    fun `together, rerouting and not navigating send only their status`() {
        assertEquals(listOf(Message.STATUS), ClusterNext.owed(REROUTING, NAVIGATING, "depart", "depart", "20 m", "300 m"))
        assertEquals(listOf(Message.STATUS), ClusterNext.owed(NOT_NAVIGATING, NAVIGATING, null, "depart", null, "300 m"))
        assertEquals(emptyList<Message>(), ClusterNext.owed(NOT_NAVIGATING, NOT_NAVIGATING, null, null, null, null))
    }

    @Test
    fun `a turn whose words change every second doesn't starve the distance`() {
        // No road name: the turn's words are the app's cue, which counts down the metres.
        val sent = run(1000, 5000) { t -> Now(NAVIGATING, "in ${500 - t / 10} m turn right", "${500 - t / 10} m") }
        assertEquals(
            listOf(Message.STATUS, Message.TURN, Message.DISTANCE, Message.TURN, Message.DISTANCE, Message.TURN),
            sent.map { it.second },
        )
    }

    @Test
    fun `right after a turn a waiting distance goes first, but never before a status`() {
        assertEquals(Message.DISTANCE, ClusterNext.of(NAVIGATING, NAVIGATING, "b", "a", "20 m", "30 m", last = Message.TURN))
        assertEquals(Message.TURN, ClusterNext.of(NAVIGATING, NAVIGATING, "b", "a", "20 m", "30 m", last = Message.DISTANCE))
        assertEquals(Message.STATUS, ClusterNext.of(REROUTING, NAVIGATING, "b", "a", "20 m", "30 m", last = Message.TURN))
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
    fun `at the start not navigating goes out once, and nothing after`() {
        val sent = run(1000, 5000) { Now(NOT_NAVIGATING) }
        assertEquals(listOf(0L to Message.STATUS), sent)
    }

    @Test
    fun `a turn waits for the status that says navigating`() {
        assertEquals(Message.STATUS, ClusterNext.of(NAVIGATING, NOT_NAVIGATING, "depart", null, "300 m", null))
    }
}
