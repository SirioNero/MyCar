package io.github.sirionero.mycar.car

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The cars that get no turns on their instrument cluster, kept for each car as
 * [io.github.sirionero.mycar.audio.CarRoutes] knows it (its Bluetooth address, or what its head
 * unit says it is): a car that ended the session right after myCar began showing it a navigation
 * ([ClusterBlame]). They still get "not navigating" when the channel opens. The car's Settings
 * say so, and can turn turns back on. Also lends [ClusterNav] the app, to draw turns as pictures.
 */
internal object ClusterCars {

    private const val PREFS = "mycar_cluster_no_turns"

    /** The app, once [init] has run: [ClusterImage] loads the map app's icons with it. */
    @Volatile var app: Context? = null
        private set

    private val offFlow = MutableStateFlow<Set<String>>(emptySet())

    /** The cars that get no turns, by their key. */
    val off: StateFlow<Set<String>> = offFlow.asStateFlow()

    fun init(context: Context) {
        app = context.applicationContext
        offFlow.value = runCatching { prefs()?.all?.keys?.toSet() }.getOrNull().orEmpty()
    }

    /** [car] gets no more turns. */
    fun stopTurns(car: String) {
        prefs()?.edit()?.putLong(car, System.currentTimeMillis())?.apply()
        offFlow.value = offFlow.value + car
    }

    /** From the car's Settings: [car] gets turns again, from now on. */
    fun turnsAgain(car: String) {
        prefs()?.edit()?.remove(car)?.apply()
        offFlow.value = offFlow.value - car
    }

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
