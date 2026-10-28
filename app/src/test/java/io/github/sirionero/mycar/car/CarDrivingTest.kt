package io.github.sirionero.mycar.car

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDrivingTest {

    /** A SensorEventIndication carrying only the driving status: { 13: { 1: status } }. */
    private fun drivingStatus(status: Int) = byteArrayOf(0x6A, 0x02, 0x08, status.toByte())

    @After
    fun forget() {
        CarDriving.override = null
        CarDriving.gpsMoving = { false }
        CarDriving.clear()
    }

    @Test
    fun readsTheDrivingStatus() {
        assertEquals(1L, CarDriving.statusIn(drivingStatus(1)))
        assertEquals(0L, CarDriving.statusIn(drivingStatus(0)))
        assertEquals(31L, CarDriving.statusIn(drivingStatus(31)))
    }

    @Test
    fun otherSensorsSayNothing() {
        // Night mode, field 10: { 1: true }.
        assertNull(CarDriving.statusIn(byteArrayOf(0x52, 0x02, 0x08, 0x01)))
        assertNull(CarDriving.statusIn(byteArrayOf()))
    }

    @Test
    fun noVideoWhileTheCarSaysSo() {
        CarDriving.onSensorEvent(drivingStatus(1))
        assertFalse(CarDriving.videoAllowed)
        // Everything restricted but video (no keyboard, no voice): video is allowed.
        CarDriving.onSensorEvent(drivingStatus(6))
        assertTrue(CarDriving.videoAllowed)
        CarDriving.onSensorEvent(drivingStatus(31))
        assertFalse(CarDriving.videoAllowed)
        CarDriving.onSensorEvent(drivingStatus(0))
        assertTrue(CarDriving.videoAllowed)
    }

    @Test
    fun otherSensorsLeaveItAsItWas() {
        CarDriving.onSensorEvent(drivingStatus(1))
        CarDriving.onSensorEvent(byteArrayOf(0x52, 0x02, 0x08, 0x01))
        assertFalse(CarDriving.videoAllowed)
        CarDriving.onSensorEvent(drivingStatus(0))
    }

    @Test
    fun theGpsCoversItEvenWhenTheCarSaysParked() {
        // A car that said "parked" once, and nothing since.
        CarDriving.onSensorEvent(drivingStatus(0))
        CarDriving.gpsMoving = { true }
        assertFalse(CarDriving.videoAllowed)
        CarDriving.gpsMoving = { false }
        assertTrue(CarDriving.videoAllowed)
    }

    @Test
    fun withNothingFromTheCarTheGpsDecides() {
        CarDriving.clear()
        CarDriving.gpsMoving = { true }
        assertFalse(CarDriving.videoAllowed)
        CarDriving.gpsMoving = { false }
        assertTrue(CarDriving.videoAllowed)
    }

    @Test
    fun thePreviewHasTheLastWord() {
        CarDriving.onSensorEvent(drivingStatus(0))
        CarDriving.override = false
        assertFalse(CarDriving.videoAllowed)
        CarDriving.override = true
        CarDriving.onSensorEvent(drivingStatus(1))
        assertTrue(CarDriving.videoAllowed)
        CarDriving.onSensorEvent(drivingStatus(0))
    }
}
