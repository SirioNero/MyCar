package io.github.sirionero.mycar.car

import android.os.SystemClock
import androidx.car.app.model.CarText
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.Protobuf

/**
 * The next turn on the car's instrument cluster, over its NavigationStatusService (Service
 * field 8), the way Android Auto feeds it:
 *
 *   -> NavigationStatus         { status = 1 (1 active, 2 inactive, 3 rerouting) }
 *   -> NavigationNextTurnEvent  { road = 1; turn_side = 2; event = 3; image = 4; turn_number = 5; turn_angle = 6 }
 *   -> NavigationNextTurnDistanceEvent { distance_meters = 1; time_to_turn_seconds = 2;
 *                                        display_distance_e3 = 3; display_distance_unit = 4 }
 *
 * Newer cars also take a NavigationState message; the deprecated pair above is what a 2018
 * Uconnect understands. The turn comes from the map app's trip (what the Car App Library hands
 * to clusters), or from its routing card when it sends no trip.
 *
 * At the pace the car asks for ([ClusterSpec], [ClusterNext]): one message at a time for a cluster
 * that wants pictures, whose turns carry one (image = 4); the rest together, as before. A car that
 * hangs up right after a navigation opens on its cluster gets no more turns ([ClusterBlame],
 * [ClusterCars]).
 */
class ClusterNav(
    private val spec: ClusterSpec,
    /** The car, as [io.github.sirionero.mycar.audio.CarRoutes] knows it: see [ClusterCars]. */
    private val car: String,
    private val sendOnChannel: (messageId: Int, body: ByteArray) -> Unit,
) {
    val channelId: Int get() = spec.channel
    @Volatile private var running = false
    /** Stopped for good: the channel's open answer, read on another thread, can come after [stop]. */
    @Volatile private var ended = false
    private var sentStatus = -1
    private var sentTurn: String? = null
    private var sentDistance: String? = null
    private val blame = ClusterBlame()

    fun onMessage(messageId: Int, body: ByteArray) {
        when (messageId) {
            MSG_CHANNEL_OPEN_RESPONSE -> {
                val status = Protobuf.readInt32Field(body, 1) ?: 0
                MyCarLog.i("cluster: <- ChannelOpenResponse: status=$status")
                if (status == 0) start()
            }
            // The car saying when it wants turns. Sending all along is harmless, so these are only logged.
            MSG_START -> MyCarLog.i("cluster: <- start")
            MSG_STOP -> MyCarLog.i("cluster: <- stop")
            else -> MyCarLog.i("cluster: <- unhandled message id=$messageId (${body.size} bytes)")
        }
    }

    private fun start() {
        if (running || ended) return
        running = true
        if (car in ClusterCars.off.value) {
            MyCarLog.i("cluster: this car hung up right after a turn on an earlier drive - it gets none, only \"not navigating\" (its Settings in the car can turn turns back on)")
        }
        Thread({
            while (running && !ended) {
                val wait = runCatching { tick() }.getOrElse {
                    MyCarLog.w("cluster: ${it.message}")
                    TICK_MS
                }
                // In slices: a stop is seen within one, however long the car wants between messages.
                var left = wait
                while (left > 0 && running && !ended) {
                    val slice = minOf(left, TICK_MS)
                    Thread.sleep(slice)
                    left -= slice
                }
            }
        }, "mycar-cluster").start()
    }

    fun stop() {
        ended = true
        running = false
    }

    /**
     * The car ended the session, by ByeBye with [byeBye] as its reason or by dropping the connection
     * (null). Right after a navigation opened on its cluster, the cluster is the likely cause: this
     * car gets no more turns.
     */
    fun carEnded(byeBye: Int?) {
        val after = blame.blame(SystemClock.elapsedRealtime(), byeBye) ?: return
        ClusterCars.stopTurns(car)
        MyCarLog.w(
            "cluster: the car ended the session $after ms after myCar began showing it a navigation - " +
                "it gets no more turns on its cluster (its Settings in the car can turn them back on)",
        )
    }

    /**
     * Sends what the car is owed ([ClusterNext]): one message for a cluster that wants pictures, all
     * of them together for every other one. How long until the next look.
     */
    private fun tick(): Long {
        // A car that hung up over its cluster stays at "not navigating".
        val turn = if (car in ClusterCars.off.value) null else NavTurn.current()
        val status = when {
            turn == null -> ClusterNext.NOT_NAVIGATING
            turn.rerouting -> ClusterNext.REROUTING
            else -> ClusterNext.NAVIGATING
        }
        val turnKey = turn?.let { keyOf(it.step) }
        val distance = turn?.distance
        val seconds = turn?.seconds
        val distanceKey = distance?.let { "${it.displayDistance}|${it.displayUnit}|${seconds ?: -1}" }
        val messages = if (spec.oneAtATime) {
            listOfNotNull(ClusterNext.of(status, sentStatus, turnKey, sentTurn, distanceKey, sentDistance))
        } else {
            ClusterNext.owed(status, sentStatus, turnKey, sentTurn, distanceKey, sentDistance)
        }
        if (messages.isEmpty()) return TICK_MS
        for (message in messages) {
            when (message) {
                ClusterNext.Message.STATUS -> {
                    sendOnChannel(MSG_STATUS, Protobuf.varintField(1, status.toLong()))
                    MyCarLog.i("cluster: -> ${STATUS_NAMES[status]}")
                    sentStatus = status
                    if (status == ClusterNext.NOT_NAVIGATING) { sentTurn = null; sentDistance = null }
                    blame.status(status, SystemClock.elapsedRealtime())
                }
                ClusterNext.Message.TURN -> {
                    val step = turn?.step ?: continue
                    sentTurn = turnKey
                    if (sendTurn(step)) blame.turn(SystemClock.elapsedRealtime())
                }
                ClusterNext.Message.DISTANCE -> {
                    if (distance == null) continue
                    sendDistance(distance, seconds)
                    sentDistance = distanceKey
                    blame.distance(SystemClock.elapsedRealtime())
                }
            }
        }
        return spec.intervalMs
    }

    /** What tells one turn from the next for the car: road, code, side and roundabout exit, as always. */
    private fun keyOf(step: Step): String {
        val maneuver = step.maneuver
        val (event, side) = maneuver?.let { eventFor(it.type) } ?: (EVENT_UNKNOWN to SIDE_UNSPECIFIED)
        val road = step.road.text().ifBlank { step.cue.text() }
        return "$road|$event|$side|${maneuver?.roundaboutExitNumber}"
    }

    /** False when it isn't sent: a cluster that wants pictures gets no turn without one. */
    private fun sendTurn(step: Step): Boolean {
        val maneuver = step.maneuver
        val (event, side) = maneuver?.let { eventFor(it.type) } ?: (EVENT_UNKNOWN to SIDE_UNSPECIFIED)
        val road = step.road.text().ifBlank { step.cue.text() }
        var body = Protobuf.stringField(1, road) +
            Protobuf.varintField(2, side.toLong()) +
            Protobuf.varintField(3, event.toLong())
        var drawn = ""
        spec.pictureSize()?.let { (width, height) ->
            val started = SystemClock.elapsedRealtime()
            val picture = drawTurn(maneuver, event, side, width, height) ?: run {
                MyCarLog.w("cluster: no picture for this turn - not sent, as this cluster wants one")
                return false
            }
            body += Protobuf.bytesField(4, picture.png)
            val what = if (picture.appIcon) "the app's icon" else "myCar's arrow"
            drawn = ", ${width}x$height picture of $what (${picture.png.size / 1024} KB, ${SystemClock.elapsedRealtime() - started} ms)"
        }
        maneuver?.roundaboutExitNumber?.takeIf { it > 0 }?.let { body += Protobuf.varintField(5, it.toLong()) }
        maneuver?.roundaboutExitAngle?.takeIf { it > 0 }?.let { body += Protobuf.varintField(6, it.toLong()) }
        sendOnChannel(MSG_TURN, body)
        // Not the road's name: it says where the driver is going.
        MyCarLog.i("cluster: -> turn event=$event side=$side${if (road.isEmpty()) ", no road name" else ""}$drawn")
        return true
    }

    /** The turn as the picture a cluster that wants one gets ([ClusterImage]); null if it can't be made. */
    private fun drawTurn(maneuver: Maneuver?, event: Int, side: Int, width: Int, height: Int): ClusterImage.Picture? {
        val clockwise = (maneuver?.type ?: -1) in CLOCKWISE
        val arrow = ClusterArrow.of(event, side, maneuver?.roundaboutExitAngle ?: 0, clockwise)
        return runCatching { ClusterImage.picture(ClusterCars.app, maneuver?.icon, arrow, width, height, spec.opaque) }
            .onFailure { MyCarLog.w("cluster: the turn's picture failed: ${it.javaClass.simpleName}") }
            .getOrNull()
    }

    /**
     * The Car App Library's distance units use the same numbers as the protocol's (1 metres ...
     * 7 yards), so the app's own display choice passes straight through.
     */
    private fun sendDistance(distance: Distance, seconds: Long?) {
        val meters = NavTurn.toMeters(distance)
        sendOnChannel(
            MSG_DISTANCE,
            Protobuf.varintField(1, meters.toLong()) +
                Protobuf.varintField(2, (seconds ?: 0L).coerceAtLeast(0)) +
                Protobuf.varintField(3, (distance.displayDistance * 1000).toLong()) +
                Protobuf.varintField(4, distance.displayUnit.toLong()),
        )
    }

    private fun CarText?.text() = this?.toString().orEmpty()

    companion object {
        /** How often myCar looks at the map app while it has nothing to send. */
        private const val TICK_MS = 500L
        private const val MSG_CHANNEL_OPEN_RESPONSE = 8
        private const val MSG_START = 32769
        private const val MSG_STOP = 32770
        private const val MSG_STATUS = 32771
        private const val MSG_TURN = 32772
        private const val MSG_DISTANCE = 32773

        private val STATUS_NAMES = mapOf(1 to "navigating", 2 to "not navigating", 3 to "rerouting")

        /** Roundabouts that traffic goes round clockwise, as where people drive on the left. */
        private val CLOCKWISE = setOf(
            Maneuver.TYPE_ROUNDABOUT_ENTER_CW, Maneuver.TYPE_ROUNDABOUT_EXIT_CW,
            Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW, Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE,
        )

        private const val SIDE_LEFT = 1
        private const val SIDE_RIGHT = 2
        private const val SIDE_UNSPECIFIED = 3

        private const val EVENT_UNKNOWN = 0

        /** The car's turn icon and side for a Car App Library manoeuvre. */
        fun eventFor(type: Int): Pair<Int, Int> = when (type) {
            Maneuver.TYPE_DEPART -> 1 to SIDE_UNSPECIFIED
            Maneuver.TYPE_NAME_CHANGE -> 2 to SIDE_UNSPECIFIED
            Maneuver.TYPE_KEEP_LEFT, Maneuver.TYPE_TURN_SLIGHT_LEFT -> 3 to SIDE_LEFT
            Maneuver.TYPE_KEEP_RIGHT, Maneuver.TYPE_TURN_SLIGHT_RIGHT -> 3 to SIDE_RIGHT
            Maneuver.TYPE_TURN_NORMAL_LEFT -> 4 to SIDE_LEFT
            Maneuver.TYPE_TURN_NORMAL_RIGHT -> 4 to SIDE_RIGHT
            Maneuver.TYPE_TURN_SHARP_LEFT -> 5 to SIDE_LEFT
            Maneuver.TYPE_TURN_SHARP_RIGHT -> 5 to SIDE_RIGHT
            Maneuver.TYPE_U_TURN_LEFT -> 6 to SIDE_LEFT
            Maneuver.TYPE_U_TURN_RIGHT -> 6 to SIDE_RIGHT
            Maneuver.TYPE_ON_RAMP_SLIGHT_LEFT, Maneuver.TYPE_ON_RAMP_NORMAL_LEFT, Maneuver.TYPE_ON_RAMP_SHARP_LEFT,
            Maneuver.TYPE_ON_RAMP_U_TURN_LEFT -> 7 to SIDE_LEFT
            Maneuver.TYPE_ON_RAMP_SLIGHT_RIGHT, Maneuver.TYPE_ON_RAMP_NORMAL_RIGHT, Maneuver.TYPE_ON_RAMP_SHARP_RIGHT,
            Maneuver.TYPE_ON_RAMP_U_TURN_RIGHT -> 7 to SIDE_RIGHT
            Maneuver.TYPE_OFF_RAMP_SLIGHT_LEFT, Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT -> 8 to SIDE_LEFT
            Maneuver.TYPE_OFF_RAMP_SLIGHT_RIGHT, Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT -> 8 to SIDE_RIGHT
            Maneuver.TYPE_FORK_LEFT -> 9 to SIDE_LEFT
            Maneuver.TYPE_FORK_RIGHT -> 9 to SIDE_RIGHT
            Maneuver.TYPE_MERGE_LEFT -> 10 to SIDE_LEFT
            Maneuver.TYPE_MERGE_RIGHT -> 10 to SIDE_RIGHT
            Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED -> 10 to SIDE_UNSPECIFIED
            Maneuver.TYPE_ROUNDABOUT_ENTER_CW, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW -> 11 to SIDE_UNSPECIFIED
            Maneuver.TYPE_ROUNDABOUT_EXIT_CW, Maneuver.TYPE_ROUNDABOUT_EXIT_CCW -> 12 to SIDE_UNSPECIFIED
            Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW, Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE,
            Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE -> 13 to SIDE_UNSPECIFIED
            Maneuver.TYPE_STRAIGHT -> 14 to SIDE_UNSPECIFIED
            Maneuver.TYPE_FERRY_BOAT, Maneuver.TYPE_FERRY_BOAT_LEFT, Maneuver.TYPE_FERRY_BOAT_RIGHT -> 16 to SIDE_UNSPECIFIED
            Maneuver.TYPE_FERRY_TRAIN, Maneuver.TYPE_FERRY_TRAIN_LEFT, Maneuver.TYPE_FERRY_TRAIN_RIGHT -> 17 to SIDE_UNSPECIFIED
            Maneuver.TYPE_DESTINATION, Maneuver.TYPE_DESTINATION_STRAIGHT -> 19 to SIDE_UNSPECIFIED
            Maneuver.TYPE_DESTINATION_LEFT -> 19 to SIDE_LEFT
            Maneuver.TYPE_DESTINATION_RIGHT -> 19 to SIDE_RIGHT
            else -> EVENT_UNKNOWN to SIDE_UNSPECIFIED
        }
    }
}

/** One turn as the car wants it, from whichever source the map app fills. */
internal class NavTurn(val step: Step, val distance: Distance?, val seconds: Long?, val rerouting: Boolean) {
    companion object {
        /** The map app's next turn: from its trip (what clusters get), or its routing card. */
        fun current(): NavTurn? {
            val nav = CarServices.navOrNull() ?: return null
            nav.trip.value?.let { trip ->
                val step = trip.steps.firstOrNull() ?: return@let
                val estimate = trip.stepTravelEstimates.firstOrNull()
                return NavTurn(step, estimate?.remainingDistance, estimate?.remainingTimeSeconds?.takeIf { it >= 0 }, trip.isLoading)
            }
            val info = (nav.template.value as? NavigationTemplate)?.navigationInfo as? RoutingInfo ?: return null
            val step = info.currentStep ?: return null
            return NavTurn(step, info.currentDistance, null, info.isLoading)
        }

        fun toMeters(d: Distance): Int = (d.displayDistance * when (d.displayUnit) {
            Distance.UNIT_KILOMETERS, Distance.UNIT_KILOMETERS_P1 -> 1000.0
            Distance.UNIT_MILES, Distance.UNIT_MILES_P1 -> 1609.344
            Distance.UNIT_FEET -> 0.3048
            Distance.UNIT_YARDS -> 0.9144
            else -> 1.0
        }).toInt()
    }
}
