package io.github.sirionero.mycar.car

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What myCar keeps of each car's cluster ([ClusterRecord]), for each car as
 * [io.github.sirionero.mycar.audio.CarRoutes] knows it (its Bluetooth address, or what its head
 * unit says it is): the cars that get no turns any more, having ended the session right after a
 * navigation opened on their cluster twice in a row ([ClusterBlame]); the hang-ups in a row so far;
 * the cars that have shown a navigation there. A car without turns still gets "not navigating" when
 * the channel opens. The car's Settings say so, and can turn turns back on; the car's screen says it
 * once, at the next drive ([noticeDue]). Also lends [ClusterNav] the app, to draw turns as pictures.
 */
internal object ClusterCars {

    /** The cars without turns (each with when it lost them). */
    private const val PREFS = "mycar_cluster_no_turns"
    private const val PREFS_STRIKES = "mycar_cluster_strikes"
    private const val PREFS_HELD = "mycar_cluster_held"
    /** The cars that lost their turns and whose screen hasn't said so yet. */
    private const val PREFS_NOTICE = "mycar_cluster_notice"

    /** The app, once [init] has run: [ClusterImage] loads the map app's icons with it. */
    @Volatile var app: Context? = null
        private set

    private val offFlow = MutableStateFlow<Set<String>>(emptySet())

    /** The cars that get no turns, by their key. */
    val off: StateFlow<Set<String>> = offFlow.asStateFlow()

    fun init(context: Context) {
        app = context.applicationContext
        offFlow.value = runCatching { prefs(PREFS)?.all?.keys?.toSet() }.getOrNull().orEmpty()
    }

    @Synchronized
    fun record(car: String): ClusterRecord = ClusterRecord(
        strikes = runCatching { prefs(PREFS_STRIKES)?.getInt(car, 0) }.getOrNull() ?: 0,
        held = runCatching { prefs(PREFS_HELD)?.getBoolean(car, false) }.getOrNull() == true,
        off = car in offFlow.value,
    )

    /** [car] hung up right after a navigation opened on its cluster: what that makes of it. */
    @Synchronized
    fun hungUp(car: String): ClusterRecord = save(car, record(car).hungUp())

    /** A navigation's opening went through on [car]: true when that started a count over. */
    @Synchronized
    fun tookIt(car: String): Boolean {
        val before = record(car)
        save(car, before.tookIt())
        return before.strikes > 0
    }

    /** [car] showed a navigation on its cluster for a while: never blamed again. */
    @Synchronized
    fun showedIt(car: String) {
        save(car, record(car).showedIt())
    }

    /** Whether [car]'s screen still has to say that it gets no turns (it lost them at an earlier drive). */
    @Synchronized
    fun noticeDue(car: String): Boolean = runCatching { prefs(PREFS_NOTICE)?.getBoolean(car, false) }.getOrNull() == true

    /** [car]'s screen said it. */
    @Synchronized
    fun noticeGiven(car: String) {
        prefs(PREFS_NOTICE)?.edit()?.remove(car)?.apply()
    }

    /** From the car's Settings: [car] gets turns again, from now on. */
    @Synchronized
    fun turnsAgain(car: String) {
        save(car, record(car).turnsAgain())
    }

    private fun save(car: String, record: ClusterRecord): ClusterRecord {
        val strikes = prefs(PREFS_STRIKES)?.edit()
        if (record.strikes > 0) strikes?.putInt(car, record.strikes) else strikes?.remove(car)
        strikes?.apply()
        if (record.held) prefs(PREFS_HELD)?.edit()?.putBoolean(car, true)?.apply()
        if (record.off != (car in offFlow.value)) {
            val off = prefs(PREFS)?.edit()
            if (record.off) off?.putLong(car, System.currentTimeMillis()) else off?.remove(car)
            off?.apply()
            // Lost now: said at the next drive; given back: nothing to say any more.
            val notice = prefs(PREFS_NOTICE)?.edit()
            if (record.off) notice?.putBoolean(car, true) else notice?.remove(car)
            notice?.apply()
            offFlow.value = if (record.off) offFlow.value + car else offFlow.value - car
        }
        return record
    }

    private fun prefs(name: String) = app?.getSharedPreferences(name, Context.MODE_PRIVATE)
}
