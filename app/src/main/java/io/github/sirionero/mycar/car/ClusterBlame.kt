package io.github.sirionero.mycar.car

/**
 * Whether a car that ended the session did so over what myCar had just shown on its cluster. The
 * 2024 MG ZS of issue #23 hung up half a second after its first turn, at every destination; a car
 * that does it twice in a row ([ClusterRecord]) is then left alone ([ClusterCars]): no more turns,
 * only "not navigating" when the channel opens, which it takes.
 *
 * Only the messages that open a navigation count - its status, its first turn and its first
 * distance - and only for [WINDOW_MS] after the last of them: a car that can't take turns hangs up
 * at the first one, while a Wi-Fi drop a few seconds after any turn of a long drive would take them
 * from a car that shows them well. A navigation called off ("not navigating") closes the window. A
 * ByeBye that says the driver or the car chose to end isn't the cluster's doing either. While the
 * session lasts, the clock says when an opening went through and when the car has shown a
 * navigation for [HELD_MS] ([check]). Fed from the cluster's thread and asked from the one that
 * reads the car.
 */
internal class ClusterBlame {

    /** What the clock says of the navigation under way ([check]). */
    enum class Event {
        /** The window after its opening passed with the session up: the car took it. */
        SURVIVED,

        /** The car has shown it for [HELD_MS] since its first turn. */
        HELD,
    }

    private var navigating = false
    private var turnSent = false
    private var distanceSent = false

    /** When the last message opening a navigation went out, on a clock that only goes forward; null outside the window. */
    private var openedAt: Long? = null

    /** When the navigation's first turn went out; null before it, and once [Event.HELD] is said. */
    private var firstTurnAt: Long? = null

    /** The session is over ([blame] was asked): the clock has nothing more to say. */
    private var over = false

    /** A status went out at [now]: a navigation (or a reroute) after none opens one; none calls it off. */
    @Synchronized
    fun status(status: Int, now: Long) {
        if (status == ClusterNext.NOT_NAVIGATING) {
            navigating = false
            openedAt = null
            firstTurnAt = null
            return
        }
        if (navigating) return
        navigating = true
        turnSent = false
        distanceSent = false
        openedAt = now
    }

    @Synchronized
    fun turn(now: Long) {
        if (!navigating || turnSent) return
        turnSent = true
        openedAt = now
        firstTurnAt = now
    }

    @Synchronized
    fun distance(now: Long) {
        if (!navigating || distanceSent) return
        distanceSent = true
        openedAt = now
    }

    /** What the clock says at [now], with the session up: each [Event] once, null otherwise. */
    @Synchronized
    fun check(now: Long): Event? {
        if (over) return null
        val opened = openedAt
        if (opened != null && now - opened > WINDOW_MS) {
            openedAt = null
            return Event.SURVIVED
        }
        val first = firstTurnAt
        if (first != null && now - first >= HELD_MS) {
            firstTurnAt = null
            return Event.HELD
        }
        return null
    }

    /**
     * The car ended the session at [now], by ByeBye with [byeBye] as its reason, or by dropping the
     * connection (null). How long after the navigation's last opening message, when that looks like
     * the cluster's doing; null when it doesn't.
     */
    @Synchronized
    fun blame(now: Long, byeBye: Int?): Long? {
        over = true
        val at = openedAt ?: return null
        if (byeBye != null && byeBye in CHOSEN) return null
        return (now - at).takeIf { it in 0..WINDOW_MS }
    }

    companion object {
        const val WINDOW_MS = 3_000L

        /** A navigation shown this long after its first turn: the car takes turns, and is never blamed. */
        const val HELD_MS = 60_000L

        /**
         * ByeBye reasons that say someone chose to end: USER_SELECTION 1, DEVICE_SWITCH 2,
         * WIRELESS_PROJECTION_DISABLED 6, POWER_DOWN 7, USER_PROFILE_SWITCH 8.
         */
        val CHOSEN = setOf(1, 2, 6, 7, 8)
    }
}
