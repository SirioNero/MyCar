package io.github.sirionero.mycar.car

/**
 * What myCar keeps of one car's cluster from drive to drive ([ClusterCars]): how many times in a row
 * it hung up right after a navigation opened there ([ClusterBlame]), whether it has ever shown a
 * navigation there for a while ([held]), and whether it gets no turns any more ([off]).
 *
 * One hang-up could be a Wi-Fi or a cable dropping, or the car switched off, at the wrong moment:
 * only [TIMES] in a row take the turns away, as [io.github.sirionero.mycar.HeldScreen] counts its
 * drives. A navigation whose opening goes through starts the count over, and a car that has shown
 * a navigation is never blamed again.
 */
internal data class ClusterRecord(val strikes: Int = 0, val held: Boolean = false, val off: Boolean = false) {

    /** One more hang-up right after a navigation opened on the cluster. */
    fun hungUp(): ClusterRecord {
        if (held || off) return this
        val now = strikes + 1
        return copy(strikes = now, off = now >= TIMES)
    }

    /** A navigation's opening went through: the hang-ups in a row start over. */
    fun tookIt(): ClusterRecord = if (strikes == 0) this else copy(strikes = 0)

    /** The car showed a navigation on its cluster for a while: never blamed again. */
    fun showedIt(): ClusterRecord = copy(strikes = 0, held = true)

    /** From the car's Settings: turns again, and the count from nothing. */
    fun turnsAgain(): ClusterRecord = copy(strikes = 0, off = false)

    companion object {
        const val TIMES = 2
    }
}
