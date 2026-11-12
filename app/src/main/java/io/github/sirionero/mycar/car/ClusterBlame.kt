package io.github.sirionero.mycar.car

/**
 * Whether a car that ended the session did so over what myCar had just shown on its cluster. The
 * 2024 MG ZS of issue #23 hung up half a second after its first turn, at every destination; a car
 * like that is then left alone ([ClusterCars]): no more turns, only "not navigating" when the
 * channel opens, which it takes.
 *
 * Only the messages that open a navigation count - its status, its first turn and its first
 * distance - and only for [WINDOW_MS] after the last of them: a car that can't take turns hangs up
 * at the first one, while a Wi-Fi drop a few seconds after any turn of a long drive would take them
 * from a car that shows them well. A ByeBye that says the driver or the car chose to end isn't the
 * cluster's doing either. Fed from the cluster's thread and asked from the one that reads the car.
 */
internal class ClusterBlame {

    private var navigating = false
    private var turnSent = false
    private var distanceSent = false

    /** When the last message opening a navigation went out, on a clock that only goes forward. */
    private var openedAt: Long? = null

    /** A status went out at [now]: a navigation (or a reroute) after none opens one. */
    @Synchronized
    fun status(status: Int, now: Long) {
        if (status == ClusterNext.NOT_NAVIGATING) {
            navigating = false
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
    }

    @Synchronized
    fun distance(now: Long) {
        if (!navigating || distanceSent) return
        distanceSent = true
        openedAt = now
    }

    /**
     * The car ended the session at [now], by ByeBye with [byeBye] as its reason, or by dropping the
     * connection (null). How long after the navigation's last opening message, when that looks like
     * the cluster's doing; null when it doesn't.
     */
    @Synchronized
    fun blame(now: Long, byeBye: Int?): Long? {
        val at = openedAt ?: return null
        if (byeBye != null && byeBye in CHOSEN) return null
        return (now - at).takeIf { it in 0..WINDOW_MS }
    }

    companion object {
        const val WINDOW_MS = 3_000L

        /**
         * ByeBye reasons that say someone chose to end: USER_SELECTION 1, DEVICE_SWITCH 2,
         * WIRELESS_PROJECTION_DISABLED 6, POWER_DOWN 7, USER_PROFILE_SWITCH 8.
         */
        val CHOSEN = setOf(1, 2, 6, 7, 8)
    }
}
