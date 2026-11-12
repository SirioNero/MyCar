package io.github.sirionero.mycar.car

/**
 * Whether telling a car the phone's Bluetooth address (written in Settings > Connection, sent by
 * CarBluetooth) takes the phone's Bluetooth music away from it. The address is for the few cars that
 * drop the phone's Bluetooth, or hang up, without it (a 2021 Bolt, a 2020 Civic). Other cars take it
 * as Android Auto's word that the music now comes through the projection: a 2018 Golf (MIB Standard2,
 * wireless through an adapter) took the phone's music over Bluetooth at every drive before the
 * address was written, and at none after. Such a car isn't told the address again.
 *
 * For each car already paired, one look a while after it showed myCar's screen:
 * - Not told the address, [LOOK_UNTOLD_MS] in: its music is connected over Bluetooth. Remembered: the
 *   car takes the phone's music over Bluetooth without the address, at a drive that worked that long.
 * - Told, [LOOK_TOLD_MS] in: its music isn't connected over Bluetooth while its calls are. If it was
 *   remembered, the address took the music away: myCar stops telling it.
 * A car that never takes the phone's music over Bluetooth (a VW Tayron keeps it for calls) is never
 * remembered; nor is one that needs the address, which without it hangs up within seconds, keeps its
 * own screen, or drops the phone's Bluetooth. A call puts the look off: some cars drop the music's
 * Bluetooth for one, and take a few seconds to bring it back.
 *
 * One drive's part, without Android: PhoneAddressCheck looks and tells this what it saw.
 */
internal class PhoneAddressGuard {

    enum class Verdict {
        /** Look again in [AGAIN_MS]: a call is on, or just ended. */
        LATER,

        /** Nothing to learn from this drive. */
        NOTHING,

        /** Not told the address, the car takes the phone's music over Bluetooth: remembered for it. */
        MUSIC_WITHOUT,

        /** Told the address, the car lost the phone's Bluetooth music it takes without: not told again. */
        STOP_TELLING,
    }

    /** Looks put off by calls this drive. */
    private var waits = 0

    /** A call was on at the last look. */
    private var sawCall = false

    /**
     * The look: the car was [told] the address this drive or not, and was remembered taking the
     * phone's music over Bluetooth without it ([musicWithout]); right now its [music] (A2DP) and its
     * [calls] (hands-free) are connected to the phone or not, and a call is on or not ([inCall]).
     */
    fun look(told: Boolean, musicWithout: Boolean, music: Boolean, calls: Boolean, inCall: Boolean): Verdict {
        if (inCall || sawCall) {
            // In a call, or just out of one: the car gets a moment to bring its music back. Not for
            // ever, as an app can leave the phone "in communication": then nothing is learnt.
            sawCall = inCall
            return if (waits++ < MAX_WAITS) Verdict.LATER else Verdict.NOTHING
        }
        return when {
            !told -> if (music) Verdict.MUSIC_WITHOUT else Verdict.NOTHING
            // Told, and the music still there: the address took nothing away.
            music -> Verdict.NOTHING
            // Nothing of the car on Bluetooth, calls included: a car switching off, the phone's
            // Bluetooth off. Not the address's doing.
            !calls -> Verdict.NOTHING
            // Never seen with the music without the address: a car that takes only calls over
            // Bluetooth, or one that needs the address.
            !musicWithout -> Verdict.NOTHING
            else -> Verdict.STOP_TELLING
        }
    }

    companion object {
        /**
         * Told the address, the look comes this long after the car showed myCar: the car has had the
         * address since before that, and long to connect its music if it would.
         */
        const val LOOK_TOLD_MS = 2 * 60_000L

        /**
         * Not told, this long: only a drive that worked this long without the address counts. A car
         * that needs it doesn't get there: a 2021 Bolt hung up 25 s in, a 2020 Civic kept its screen.
         */
        const val LOOK_UNTOLD_MS = 5 * 60_000L

        /** Between looks while a call is on, and after it. */
        const val AGAIN_MS = 15_000L

        /** Looks put off by calls before the drive is given up: half an hour. */
        const val MAX_WAITS = 120
    }
}
