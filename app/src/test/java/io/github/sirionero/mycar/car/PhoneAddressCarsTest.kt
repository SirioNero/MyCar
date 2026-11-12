package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What myCar keeps of each car about the phone's Bluetooth address, over each link. */
class PhoneAddressCarsTest {

    private val settings = mutableMapOf<String, String>()
    private val cars = PhoneAddressCars({ settings[it].orEmpty() }, { key, value -> settings[key] = value })

    @Test fun `music without the address is kept for the link it was seen on`() {
        // The Golf over its wireless adapter: Wi-Fi only.
        assertTrue(cars.rememberMusicWithout("golf", wireless = true))
        assertTrue(cars.tookMusicWithout("golf", wireless = true))
        assertFalse(cars.tookMusicWithout("golf", wireless = false))
        // Once: the second time says it was there already.
        assertFalse(cars.rememberMusicWithout("golf", wireless = true))
        assertEquals("golf", settings[PhoneAddressCars.MUSIC_WIRELESS])
        // Another car, over the cable.
        assertTrue(cars.rememberMusicWithout("tayron", wireless = false))
        assertTrue(cars.tookMusicWithout("tayron", wireless = false))
        assertFalse(cars.tookMusicWithout("tayron", wireless = true))
    }

    @Test fun `a car no longer told, until the address is saved again`() {
        assertFalse(cars.keptFromSome())
        assertTrue(cars.stopTelling("golf"))
        assertFalse(cars.stopTelling("golf"))
        assertTrue(cars.notTold("golf"))
        assertFalse(cars.notTold("tayron"))
        assertTrue(cars.keptFromSome())
        cars.addressSaved()
        assertFalse(cars.notTold("golf"))
        assertFalse(cars.keptFromSome())
    }

    @Test fun `saved again, what made myCar keep it from a car is forgotten on both links, the rest kept`() {
        cars.rememberMusicWithout("golf", wireless = true)
        cars.rememberMusicWithout("golf", wireless = false)
        cars.rememberMusicWithout("other", wireless = true)
        cars.stopTelling("golf")
        cars.addressSaved()
        // Its next drive is told the address, and doesn't lose it again: only a new drive without can.
        assertFalse(cars.tookMusicWithout("golf", wireless = true))
        assertFalse(cars.tookMusicWithout("golf", wireless = false))
        assertTrue(cars.tookMusicWithout("other", wireless = true))
    }

    @Test fun `saved with no car kept from it - nothing touched`() {
        cars.rememberMusicWithout("golf", wireless = true)
        val before = settings.toMap()
        cars.addressSaved()
        assertEquals(before, settings)
        assertTrue(cars.tookMusicWithout("golf", wireless = true))
    }

    @Test fun `nothing kept reads as nothing`() {
        assertFalse(cars.tookMusicWithout("golf", wireless = true))
        assertFalse(cars.notTold("golf"))
        assertFalse(cars.keptFromSome())
    }
}
