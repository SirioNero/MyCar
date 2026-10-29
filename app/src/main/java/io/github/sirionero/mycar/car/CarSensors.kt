package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Wire
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the head unit's sensor channel has offered and sent, for the "Vehicle data" screen.
 *
 * [ServiceDiscovery.findSensorService] tells us which numeric sensor types this head unit is
 * willing to stream; [SensorType] names the ones publicly documented by other AA reverse-engineering
 * projects (aasdk, openauto). That mapping is carried over from other projects, not verified
 * against this exact unit, so it's a label, not a guarantee - the raw field dump next to it is
 * the ground truth. Once [onEvent] starts decoding real traffic from the 2018 Uconnect, correct
 * any type that turns out to be mislabeled.
 */
object CarSensors {

    /** [raw] is null until the head unit sends data for the type. Its name: [SensorType.name]. */
    data class Reading(val typeId: Int, val raw: String?, val updatedAtMs: Long)

    private val readingsFlow = MutableStateFlow<List<Reading>>(emptyList())
    val readings: StateFlow<List<Reading>> = readingsFlow

    /** Seeds one row per sensor type the head unit advertised, so the screen shows what's on
     * offer even before the first event for it arrives. */
    fun onDiscovered(types: List<Int>) {
        // Once each: a type listed twice made the screen's list throw on its repeated key.
        readingsFlow.value = types.distinct().map { id ->
            Reading(id, null, 0L)
        }
    }

    /**
     * One SensorEventIndication. Every reverse-engineered version of this message multiplexes
     * every sensor type into one shared set of message-level field numbers (field N = "the data
     * for sensor type N", roughly) rather than tagging events with the type id from
     * SensorType.type - so field number is read here as a best-effort type id, same as the
     * other services in this file.
     */
    fun onEvent(body: ByteArray) {
        // Only for the "Vehicle data" screen, off unless both settings are on: every event was
        // written out on the car's reading thread, drive after drive, for a screen nobody opened.
        if (!CarSettings.experimentalFeaturesEnabled.value || !CarSettings.carSensorsEnabled.value) return
        val now = System.currentTimeMillis()
        val fields = Wire.fields(body)
        val byType = fields.groupBy { it.number }
        val updated = byType.mapValues { (number, group) ->
            Reading(
                number,
                group.joinToString("; ") { field ->
                    field.bytes?.let { Protobuf.describe(it, indent = "").trim().replace("\n", ", ") }
                        ?: "varint=${field.varint}"
                },
                now,
            )
        }
        val existing = readingsFlow.value.associateBy { it.typeId }
        readingsFlow.value = (existing + updated).values.sortedBy { it.typeId }
    }

    fun clear() {
        readingsFlow.value = emptyList()
    }
}

/**
 * Sensor type ids as named across public Android Auto reverse-engineering references. Treated as
 * a label, not a fact - see [CarSensors] doc.
 */
object SensorType {
    private val NAMES = mapOf(
        1 to R.string.car_sensor_location,
        2 to R.string.car_sensor_compass,
        3 to R.string.car_sensor_speed,
        4 to R.string.car_sensor_rpm,
        5 to R.string.car_sensor_odometer,
        6 to R.string.car_sensor_fuel,
        7 to R.string.car_sensor_parking_brake,
        8 to R.string.car_sensor_gear,
        9 to R.string.car_sensor_diagnostics,
        10 to R.string.car_sensor_night_mode,
        11 to R.string.car_sensor_environment,
        12 to R.string.car_sensor_hvac,
        13 to R.string.car_sensor_driving_status,
        14 to R.string.car_sensor_dead_reckoning,
        15 to R.string.car_sensor_passenger,
        16 to R.string.car_sensor_door,
        17 to R.string.car_sensor_light,
        18 to R.string.car_sensor_tire_pressure,
        19 to R.string.car_sensor_accelerometer,
        20 to R.string.car_sensor_gyroscope,
        21 to R.string.car_sensor_gps_satellites,
        22 to R.string.car_sensor_toll_card,
    )

    /** The string resource naming [id]; null for a type with no known name. */
    fun name(id: Int): Int? = NAMES[id]
}
