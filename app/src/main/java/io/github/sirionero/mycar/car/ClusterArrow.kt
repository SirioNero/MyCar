package io.github.sirionero.mycar.car

/**
 * The plain arrow myCar draws for a turn when the map app gives no icon of its own, or it won't
 * load ([ClusterImage]): worked out from the turn's code as the cluster gets it
 * ([ClusterNav.eventFor]). [bend] is where it points, in degrees clockwise from straight ahead
 * (90 a right turn, -90 a left one); for a U-turn its sign says which way.
 */
internal class ClusterArrow(val kind: Kind, val bend: Int) {

    enum class Kind { ARROW, U_TURN, ROUNDABOUT, DESTINATION }

    /** The square a turn is drawn in, within the picture. */
    data class Box(val left: Int, val top: Int, val side: Int)

    /** A rectangle, edges in pixels. */
    data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int)

    companion object {
        // NavigationNextTurnEvent's NextTurnEnum and TurnSide, as ClusterNav sends them.
        private const val SLIGHT_TURN = 3
        private const val TURN = 4
        private const val SHARP_TURN = 5
        private const val U_TURN = 6
        private const val ON_RAMP = 7
        private const val OFF_RAMP = 8
        private const val FORK = 9
        private const val MERGE = 10
        private const val ROUNDABOUT_ENTER = 11
        private const val ROUNDABOUT_EXIT = 12
        private const val ROUNDABOUT_ENTER_AND_EXIT = 13
        private const val DESTINATION = 19
        private const val LEFT = 1
        private const val RIGHT = 2

        /**
         * The arrow for a turn: [event] and [side] as the cluster gets them; [exitAngle] a
         * roundabout's, from its entry the way traffic goes round (0 when unknown), and
         * [clockwise] whether traffic goes round clockwise, as where people drive on the left.
         */
        fun of(event: Int, side: Int, exitAngle: Int = 0, clockwise: Boolean = false): ClusterArrow {
            val sign = when (side) {
                LEFT -> -1
                RIGHT -> 1
                else -> 0
            }
            return when (event) {
                SLIGHT_TURN, ON_RAMP, OFF_RAMP, FORK, MERGE -> ClusterArrow(Kind.ARROW, 45 * sign)
                TURN -> ClusterArrow(Kind.ARROW, 90 * sign)
                SHARP_TURN -> ClusterArrow(Kind.ARROW, 135 * sign)
                // A U-turn with no side goes left, as where people drive on the right.
                U_TURN -> ClusterArrow(Kind.U_TURN, if (sign > 0) 180 else -180)
                ROUNDABOUT_ENTER, ROUNDABOUT_EXIT, ROUNDABOUT_ENTER_AND_EXIT ->
                    ClusterArrow(Kind.ROUNDABOUT, roundaboutBend(exitAngle, clockwise))
                DESTINATION -> ClusterArrow(Kind.DESTINATION, 0)
                // Depart, straight on, a new road name, ferries, and anything unknown.
                else -> ClusterArrow(Kind.ARROW, 0)
            }
        }

        /**
         * Where a roundabout's exit leads, from its angle: going round anticlockwise (driving on
         * the right) the exit at 90 degrees is the first on the right, 180 straight on and 270 on
         * the left; clockwise the other way round. Straight on when the angle is unknown.
         */
        fun roundaboutBend(exitAngle: Int, clockwise: Boolean): Int {
            if (exitAngle !in 1..360) return 0
            return if (clockwise) exitAngle - 180 else 180 - exitAngle
        }

        /** The square a turn is drawn in: centred, 80% of the picture's shorter side. */
        fun box(width: Int, height: Int): Box {
            val side = maxOf(1, minOf(width, height) * 4 / 5)
            return Box((width - side) / 2, (height - side) / 2, side)
        }

        /**
         * A picture of [width] x [height] fitted in [box], centred and keeping its shape; the whole
         * box when the picture doesn't say its size.
         */
        fun fit(box: Box, width: Int, height: Int): Area {
            if (width <= 0 || height <= 0) return Area(box.left, box.top, box.left + box.side, box.top + box.side)
            val w: Int
            val h: Int
            if (width >= height) {
                w = box.side
                h = maxOf(1, (box.side.toLong() * height / width).toInt())
            } else {
                h = box.side
                w = maxOf(1, (box.side.toLong() * width / height).toInt())
            }
            val left = box.left + (box.side - w) / 2
            val top = box.top + (box.side - h) / 2
            return Area(left, top, left + w, top + h)
        }
    }
}
