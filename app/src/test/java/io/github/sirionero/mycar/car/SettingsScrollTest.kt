package io.github.sirionero.mycar.car

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScrollTest {
    @After fun nextDrive() = SettingsScroll.forget()

    @Test fun aDriveStartsAtTheTop() = assertEquals(0, SettingsScroll.restore(1.5f))

    @Test fun backWhereItWasLeft() {
        SettingsScroll.keep(700, 1.5f)
        assertEquals(700, SettingsScroll.restore(1.5f))
    }

    /** Large is 1.25 times the density: the same place, in more pixels. */
    @Test fun samePlaceAtAnotherSize() {
        SettingsScroll.keep(700, 1.5f)
        assertEquals(875, SettingsScroll.restore(1.5f * 1.25f))
    }

    @Test fun theNextDriveAtTheTop() {
        SettingsScroll.keep(700, 1.5f)
        SettingsScroll.forget()
        assertEquals(0, SettingsScroll.restore(1.5f))
    }
}
