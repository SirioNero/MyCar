package io.github.sirionero.mycar.car

/**
 * Which of the cluster's messages go now, each with the newest state: the status first, then the
 * turn, then how far it is - a turn and a distance only while navigating (not while rerouting), as
 * always. A cluster that wants pictures gets one message at a time, [ClusterSpec.intervalMs] apart:
 * the 2024 MG ZS of issue #23 asks for a second between messages, and got the status, the turn and
 * the distance in the same millisecond. Every other one gets what it's owed together, as myCar always
 * sent it, in rounds that keep the car's interval: the cars that took turns before take them as before.
 */
internal object ClusterNext {

    enum class Message { STATUS, TURN, DISTANCE }

    // NavigationStatus { status = 1 }: what the car is told.
    const val NAVIGATING = 1
    const val NOT_NAVIGATING = 2
    const val REROUTING = 3

    /**
     * Every message the car is owed, in order; empty when it has the newest of everything. [turn] and
     * [distance] stand for what they say (null when there's none), the sent ones for what went last.
     */
    fun owed(
        status: Int,
        sentStatus: Int,
        turn: String?,
        sentTurn: String?,
        distance: String?,
        sentDistance: String?,
    ): List<Message> = buildList {
        if (status != sentStatus) add(Message.STATUS)
        if (status == NAVIGATING) {
            if (turn != null && turn != sentTurn) add(Message.TURN)
            if (distance != null && distance != sentDistance) add(Message.DISTANCE)
        }
    }

    /**
     * The one message that goes next, for a cluster that takes one at a time; null when none is owed.
     * Right after a turn ([last]) a distance waiting goes before a newer turn: a turn whose words
     * change every second (a cue that counts down the metres) would never let it through.
     */
    fun of(
        status: Int,
        sentStatus: Int,
        turn: String?,
        sentTurn: String?,
        distance: String?,
        sentDistance: String?,
        last: Message? = null,
    ): Message? {
        val owed = owed(status, sentStatus, turn, sentTurn, distance, sentDistance)
        if (last == Message.TURN && Message.STATUS !in owed && Message.DISTANCE in owed) return Message.DISTANCE
        return owed.firstOrNull()
    }
}
