package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.ClusterArrow.Area
import io.github.sirionero.mycar.car.ClusterArrow.Box
import io.github.sirionero.mycar.car.ClusterArrow.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/** The arrow drawn for a turn when the map app gives no icon, and where a turn sits in its picture. */
class ClusterArrowTest {

    private fun arrow(event: Int, side: Int, angle: Int = 0, clockwise: Boolean = false) =
        ClusterArrow.of(event, side, angle, clockwise).let { it.kind to it.bend }

    @Test
    fun `turns point the way they go`() {
        assertEquals(Kind.ARROW to 90, arrow(4, 2))
        assertEquals(Kind.ARROW to -90, arrow(4, 1))
        assertEquals(Kind.ARROW to -45, arrow(3, 1))
        assertEquals(Kind.ARROW to 135, arrow(5, 2))
        assertEquals(Kind.ARROW to 45, arrow(8, 2)) // off ramp on the right
        assertEquals(Kind.ARROW to -45, arrow(9, 1)) // fork, keep left
    }

    @Test
    fun `depart, straight on and anything unknown go straight up`() {
        assertEquals(Kind.ARROW to 0, arrow(1, 3))
        assertEquals(Kind.ARROW to 0, arrow(14, 3))
        assertEquals(Kind.ARROW to 0, arrow(0, 3))
        assertEquals(Kind.ARROW to 0, arrow(10, 3)) // a merge with no side
    }

    @Test
    fun `a U-turn goes round the side it says, left when it says none`() {
        assertEquals(Kind.U_TURN to -180, arrow(6, 1))
        assertEquals(Kind.U_TURN to 180, arrow(6, 2))
        assertEquals(Kind.U_TURN to -180, arrow(6, 3))
    }

    @Test
    fun `a roundabout's exit from its angle, the way traffic goes round`() {
        // Anticlockwise, driving on the right: 90 is the first exit on the right.
        assertEquals(Kind.ROUNDABOUT to 90, arrow(13, 3, 90))
        assertEquals(Kind.ROUNDABOUT to 0, arrow(13, 3, 180))
        assertEquals(Kind.ROUNDABOUT to -90, arrow(13, 3, 270))
        // Clockwise, driving on the left: 90 is the first exit on the left.
        assertEquals(Kind.ROUNDABOUT to -90, arrow(13, 3, 90, clockwise = true))
        assertEquals(Kind.ROUNDABOUT to 90, arrow(13, 3, 270, clockwise = true))
        // No angle: straight across.
        assertEquals(Kind.ROUNDABOUT to 0, arrow(11, 3))
        assertEquals(Kind.ROUNDABOUT to 0, arrow(12, 3, 400))
    }

    @Test
    fun `the destination is a mark, not an arrow`() {
        assertEquals(Kind.DESTINATION to 0, arrow(19, 2))
    }

    @Test
    fun `a turn sits centred in its picture, 80 percent of the shorter side`() {
        assertEquals(Box(672, 72, 576), ClusterArrow.box(1920, 720))
        assertEquals(Box(26, 26, 204), ClusterArrow.box(256, 256))
        assertEquals(Box(0, 0, 1), ClusterArrow.box(1, 1))
    }

    @Test
    fun `an icon keeps its shape in the box`() {
        assertEquals(Area(25, 0, 75, 100), ClusterArrow.fit(Box(0, 0, 100), 50, 100))
        assertEquals(Area(10, 35, 110, 85), ClusterArrow.fit(Box(10, 10, 100), 200, 100))
        assertEquals(Area(672, 72, 1248, 648), ClusterArrow.fit(Box(672, 72, 576), 96, 96))
        // An icon that doesn't say its size fills the box.
        assertEquals(Area(10, 10, 110, 110), ClusterArrow.fit(Box(10, 10, 100), -1, -1))
    }
}
