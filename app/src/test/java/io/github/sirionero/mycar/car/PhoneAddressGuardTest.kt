package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.PhoneAddressGuard.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telling a car the phone's Bluetooth address, and the car's Bluetooth music. */
class PhoneAddressGuardTest {

    @Test fun `a car that takes the music over Bluetooth without the address is remembered`() {
        // A Golf drive before the address was written: the music on Bluetooth 5 minutes in.
        assertEquals(Verdict.MUSIC_WITHOUT, look(told = false, musicWithout = false, music = true, calls = true))
        // Again at a later drive: the same (CarBluetooth keeps it once).
        assertEquals(Verdict.MUSIC_WITHOUT, look(told = false, musicWithout = true, music = true, calls = true))
        // Calls only, or nothing at all: nothing to remember.
        assertEquals(Verdict.NOTHING, look(told = false, musicWithout = false, music = false, calls = true))
        assertEquals(Verdict.NOTHING, look(told = false, musicWithout = false, music = false, calls = false))
    }

    @Test fun `the Golf - told the address, its music gone with its calls there - not told again`() {
        // The log of 12 November: after the PairingRequest the music never connected (07:46:18, and
        // Automatic at 07:48:20), the hands-free did (07:48:12).
        assertEquals(Verdict.STOP_TELLING, look(told = true, musicWithout = true, music = false, calls = true))
    }

    @Test fun `told, but never seen with the music without it - nothing`() {
        // The Tayron: Bluetooth for calls only, told or not, so never remembered.
        assertEquals(Verdict.NOTHING, look(told = false, musicWithout = false, music = false, calls = true))
        assertEquals(Verdict.NOTHING, look(told = true, musicWithout = false, music = false, calls = true))
        // A car that needs the address never got to a look without it: same answer. So does the
        // Golf at its first drive with myCar 0.7.6, the address written already in 0.7.5.
        assertEquals(Verdict.NOTHING, look(told = true, musicWithout = false, music = false, calls = true))
    }

    @Test fun `told, with the music still there - nothing`() {
        assertEquals(Verdict.NOTHING, look(told = true, musicWithout = true, music = true, calls = true))
        assertEquals(Verdict.NOTHING, look(told = true, musicWithout = false, music = true, calls = false))
    }

    @Test fun `told, with no calls either - a car switching off, Bluetooth off - nothing`() {
        // As at the end of the Golf's drive (08:05:33, 08:06:17): neither music nor calls.
        assertEquals(Verdict.NOTHING, look(told = true, musicWithout = true, music = false, calls = false))
    }

    @Test fun `a call puts the look off, and the one right after it`() {
        val guard = PhoneAddressGuard()
        assertEquals(Verdict.LATER, guard.look(told = true, musicWithout = true, music = false, calls = true, inCall = true))
        assertEquals(Verdict.LATER, guard.look(told = true, musicWithout = true, music = false, calls = true, inCall = true))
        // Just ended: the car gets a moment to bring its music back.
        assertEquals(Verdict.LATER, guard.look(told = true, musicWithout = true, music = false, calls = true, inCall = false))
        // Back: nothing taken away.
        assertEquals(Verdict.NOTHING, guard.look(told = true, musicWithout = true, music = true, calls = true, inCall = false))

        val after = PhoneAddressGuard()
        assertEquals(Verdict.LATER, after.look(told = true, musicWithout = true, music = false, calls = true, inCall = true))
        assertEquals(Verdict.LATER, after.look(told = true, musicWithout = true, music = false, calls = true, inCall = false))
        // Still gone once the call is over and the car had its moment: the address.
        assertEquals(Verdict.STOP_TELLING, after.look(told = true, musicWithout = true, music = false, calls = true, inCall = false))
    }

    @Test fun `a call that never ends - nothing learnt`() {
        val guard = PhoneAddressGuard()
        repeat(PhoneAddressGuard.MAX_WAITS) {
            assertEquals(Verdict.LATER, guard.look(told = true, musicWithout = true, music = false, calls = true, inCall = true))
        }
        assertEquals(Verdict.NOTHING, guard.look(told = true, musicWithout = true, music = false, calls = true, inCall = true))
        assertEquals(Verdict.NOTHING, guard.look(told = true, musicWithout = true, music = false, calls = true, inCall = false))
    }

    @Test fun `a drive counts without the address only if it worked for minutes`() {
        // A 2021 Bolt hung up 25 s in without the address: nowhere near a look.
        assertTrue(PhoneAddressGuard.LOOK_UNTOLD_MS >= 10 * 25_000L)
        // Told, the look comes sooner, but well after a car brings its music (Automatic waits 5 s).
        assertTrue(PhoneAddressGuard.LOOK_TOLD_MS in 60_000L..PhoneAddressGuard.LOOK_UNTOLD_MS)
    }

    private fun look(told: Boolean, musicWithout: Boolean, music: Boolean, calls: Boolean) =
        PhoneAddressGuard().look(told, musicWithout, music, calls, inCall = false)
}
