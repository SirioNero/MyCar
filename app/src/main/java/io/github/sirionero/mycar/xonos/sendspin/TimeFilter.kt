package io.github.sirionero.mycar.xonos.sendspin

import kotlin.math.abs
import kotlin.math.floor

/**
 * Il filtro di Kalman della specifica Sendspin: stima di quanto l'orologio del server è avanti
 * rispetto al nostro (offset) e di quanto questa differenza cambia nel tempo (drift).
 * I tempi sono in microsecondi; i parametri sono quelli raccomandati dalla specifica.
 */
class TimeFilter {

    private var offset = 0.0
    private var drift = 0.0
    private var pOO = Double.POSITIVE_INFINITY
    private var pOD = 0.0
    private var pDD = 0.0
    private var lastUpdate = 0L
    private var useDrift = false

    /** Quanto è avanti l'orologio del server, secondo la stima attuale. */
    val offsetUs: Double
        @Synchronized get() = offset

    /** Quante misure ha usato: dalla prima in poi si sa convertire i tempi. */
    @get:Synchronized
    var count = 0
        private set

    /** Una misura da uno scambio client/time - server/time (t1 inviato, t2 e t3 del server, t4 ricevuto). */
    @Synchronized
    fun update(t1: Long, t2: Long, t3: Long, t4: Long) {
        // Divisione intera che tronca verso lo zero, come chiede la specifica.
        val measurement = ((t2 - t1) + (t3 - t4)) / 2
        val maxError = ((t4 - t1) - (t3 - t2)) / 2
        update(measurement, maxError, t4)
    }

    @Synchronized
    fun update(measurement: Long, maxError: Long, timeAdded: Long) {
        if (timeAdded <= lastUpdate || maxError <= 0) return
        val dt = (timeAdded - lastUpdate).toDouble()
        lastUpdate = timeAdded
        val r = sq(MAX_ERROR_SCALE * maxError)
        val m = measurement.toDouble()
        when (count) {
            0 -> {
                offset = m
                pOO = r
                drift = 0.0
                count = 1
                return
            }
            1 -> {
                drift = (m - offset) / dt
                pDD = (pOO + r) / (dt * dt)
                offset = m
                pOO = r
                count = 2
                return
            }
        }
        val offsetP = offset + drift * dt
        var pOOp = pOO + 2 * pOD * dt + pDD * dt * dt + sq(PROCESS_STD_DEV) * dt
        var pODp = pOD + pDD * dt
        var pDDp = pDD + sq(DRIFT_PROCESS_STD_DEV) * dt
        val y = m - offsetP
        if (count < MIN_SAMPLES) {
            count++
        } else if (abs(y) > ADAPTIVE_CUTOFF * maxError) {
            val f = sq(FORGET_FACTOR)
            pOOp *= f
            pODp *= f
            pDDp *= f
        }
        val s = pOOp + r
        val kO = pOOp / s
        val kD = pODp / s
        offset = offsetP + kO * y
        drift += kD * y
        pOO = pOOp - kO * pOOp
        pOD = pODp - kD * pOOp
        pDD = pDDp - kD * pODp
        useDrift = drift * drift > sq(DRIFT_SIGNIFICANCE_THRESHOLD) * pDD
    }

    /** Il nostro orologio letto con l'orologio del server. */
    @Synchronized
    fun toServer(clientUs: Long): Long {
        val d = if (useDrift) drift else 0.0
        return clientUs + round(offset + d * (clientUs - lastUpdate))
    }

    /** Un momento dell'orologio del server sul nostro orologio. */
    @Synchronized
    fun toClient(serverUs: Long): Long {
        val d = if (useDrift) drift else 0.0
        return round((serverUs - offset + d * lastUpdate) / (1 + d))
    }

    @Synchronized
    fun reset() {
        offset = 0.0
        drift = 0.0
        pOO = Double.POSITIVE_INFINITY
        pOD = 0.0
        pDD = 0.0
        lastUpdate = 0L
        useDrift = false
        count = 0
    }

    private companion object {
        const val PROCESS_STD_DEV = 0.0
        const val DRIFT_PROCESS_STD_DEV = 1e-11
        const val FORGET_FACTOR = 2.0
        const val ADAPTIVE_CUTOFF = 3.0
        const val MIN_SAMPLES = 100
        const val DRIFT_SIGNIFICANCE_THRESHOLD = 2.0
        const val MAX_ERROR_SCALE = 0.5

        fun sq(x: Double) = x * x

        /** All'intero più vicino, con le metà lontano dallo zero. */
        fun round(x: Double): Long = if (x >= 0) floor(x + 0.5).toLong() else -floor(-x + 0.5).toLong()
    }
}
