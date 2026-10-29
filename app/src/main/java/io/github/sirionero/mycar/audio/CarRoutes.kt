package io.github.sirionero.mycar.audio

import android.content.Context
import io.github.sirionero.mycar.ServiceDiscovery

/**
 * The car audio route kept for each car. A choice made with a car connected - from the player's
 * sound button, the car's settings or the phone's - is that car's, and comes back at its next
 * drive; a choice made with no car connected is every car's, and the ones kept give way to it.
 * A car is known by its Bluetooth address or, when it gives none, by what its head unit says it is.
 */
object CarRoutes {

    private const val PREFS = "mycar_car_routes"
    private var app: Context? = null

    /** The car of the drive under way, once it has said who it is. */
    @Volatile var car: String? = null
        private set

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun keyFor(info: ServiceDiscovery.HeadUnitInfo, bluetoothAddress: String?): String =
        bluetoothAddress?.takeIf { it.isNotBlank() }?.let { "bt:" + it.uppercase() }
            ?: ("hu:" + listOf(info.carModel, info.carYear, info.headUnitMake, info.headUnitModel, info.headUnitName).joinToString("|"))

    /** The drive's car is [key]: the route kept for it, if one was. */
    fun connected(key: String): AudioRoute? {
        car = key
        return prefs()?.getString(key, null)?.let(AudioRoute::of)
    }

    fun disconnected() {
        car = null
    }

    /** Keeps [route] for the drive's car; false with no car connected. */
    fun keep(route: AudioRoute): Boolean {
        val key = car ?: return false
        prefs()?.edit()?.putString(key, route.id)?.apply()
        return true
    }

    /** A choice for every car: the ones kept for each car give way to it. */
    fun forgetAll() {
        prefs()?.edit()?.clear()?.apply()
    }

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
