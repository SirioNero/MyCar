package io.github.sirionero.mycar.car

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import io.github.sirionero.mycar.Wire
import io.github.sirionero.mycar.notify.CarMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whether video may show on the car's screen now: what the car itself says, as it does to
 * Android Auto (its driving status, which says "no video" while the car moves), and the phone's
 * GPS speed (see [CarMotion]). Either one saying the car moves covers the picture: a car that said
 * "parked" once and then nothing more can't leave video on while driving.
 */
object CarDriving {

    /** The car's own word: true while it allows video, null until it has said anything. */
    private val fromCar = MutableStateFlow<Boolean?>(null)

    /** For the car preview: the answer, whatever the car or the GPS say; null to ask them. */
    @Volatile var override: Boolean? = null

    /** The phone's GPS: true while it reads the car moving. Swapped in the tests. */
    @Volatile internal var gpsMoving: () -> Boolean = { CarMotion.moving }

    val videoAllowed: Boolean
        get() = override ?: ((fromCar.value ?: true) && !gpsMoving())

    /** A SensorEventIndication from the car: its driving status, if this one carries it. */
    fun onSensorEvent(body: ByteArray) {
        statusIn(body)?.let { fromCar.value = it and NO_VIDEO == 0L }
    }

    /** The car went away: the next one has to say for itself. */
    fun clear() {
        fromCar.value = null
    }

    /** The driving status in a sensor event (DrivingStatus { int32 status = 1 }, field 13), if any. */
    internal fun statusIn(body: ByteArray): Long? {
        val status = Wire.bytes(Wire.fields(body), SENSOR_DRIVING_STATUS) ?: return null
        return Wire.varint(Wire.fields(status), 1)
    }

    /** SensorEventIndication's field for the driving status, as the sensor type's number. */
    private const val SENSOR_DRIVING_STATUS = 13

    /** DrivingStatus's "no video" bit: set while the car moves. */
    private const val NO_VIDEO = 1L
}

/** [CarDriving.videoAllowed], checked every second: the GPS half of it has nothing to observe. */
@Composable
internal fun rememberVideoAllowed(): Boolean = produceState(CarDriving.videoAllowed) {
    while (true) {
        value = CarDriving.videoAllowed
        delay(1_000)
    }
}.value
