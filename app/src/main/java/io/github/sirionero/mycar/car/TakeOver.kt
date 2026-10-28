package io.github.sirionero.mycar.car

/**
 * Whether the app - the Plancia app, the Xonos app - takes the place of the one inside myCar for
 * the rest of the drive (see [InsideOrApp]). No Android here, so every drive can be tried on the JVM.
 *
 * Once the app has taken over it keeps the place: a car that swapped its Plancia or its music back
 * and forth would be worse than one that settles on the one that works.
 */
internal class TakeOver(
    /** Never connected yet in this drive: how long without a connection, while online, before the app takes over. */
    private val startGraceMs: Long = 10_000,
    /** Connected earlier in the drive: how long the connection may stay lost first. Longer, as a tunnel. */
    private val dropGraceMs: Long = 30_000,
) {
    var byApp = false
        private set
    private var everConnected = false
    private var downSince: Long? = null

    /**
     * [configured]: the one inside myCar has an account; [appThere]: the app is on the phone, to take
     * over. [outOfReach]: all its addresses are on a tailnet that leaves myCar out - wirelessly the
     * driver has to leave myCar out of Tailscale - and [refused]: its server turned the token down;
     * neither mends itself during the drive. [online]: the phone has internet; without it nothing
     * reaches anything, and the app would do no better.
     */
    fun update(
        now: Long,
        configured: Boolean,
        appThere: Boolean,
        connected: Boolean,
        online: Boolean,
        outOfReach: Boolean,
        refused: Boolean,
    ): Boolean {
        if (byApp) return true
        if (!configured || !appThere) {
            downSince = null
            return false
        }
        if (connected) {
            everConnected = true
            downSince = null
            return false
        }
        if (outOfReach || refused) return take()
        if (!online) {
            downSince = null
            return false
        }
        val since = downSince ?: now.also { downSince = it }
        return if (now - since >= if (everConnected) dropGraceMs else startGraceMs) take() else false
    }

    private fun take(): Boolean {
        byApp = true
        return true
    }
}
