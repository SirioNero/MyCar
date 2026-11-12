package io.github.sirionero.mycar.car

/**
 * The cluster's messages go one at a time, [ClusterSpec.intervalMs] apart, each with the newest
 * state: the 2024 MG ZS of issue #23 asks for a second between messages, and got the status, the
 * turn and the distance in the same millisecond. What goes next: the status first, then the turn,
 * then how far it is - a turn and a distance only while navigating (not while rerouting), as always.
 */
internal object ClusterNext {

    enum class Message { STATUS, TURN, DISTANCE }

    // NavigationStatus { status = 1 }: what the car is told.
    const val NAVIGATING = 1
    const val NOT_NAVIGATING = 2
    const val REROUTING = 3

    /**
     * The message the car is owed, or null when it has the newest of everything. [turn] and
     * [distance] stand for what they say (null when there's none), the sent ones for what went last.
     */
    fun of(
        status: Int,
        sentStatus: Int,
        turn: String?,
        sentTurn: String?,
        distance: String?,
        sentDistance: String?,
    ): Message? = when {
        status != sentStatus -> Message.STATUS
        status != NAVIGATING -> null
        turn != null && turn != sentTurn -> Message.TURN
        distance != null && distance != sentDistance -> Message.DISTANCE
        else -> null
    }
}
