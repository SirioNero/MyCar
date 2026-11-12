package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.Protobuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The NavigationStatusService a car describes in its ServiceDiscoveryResponse. The MG is the 2024
 * MG ZS of issue #23, as its log printed it (its Bluetooth address and serial left out).
 */
class ClusterSpecTest {

    private fun v(field: Int, value: Long) = Protobuf.varintField(field, value)
    private fun len(field: Int, body: ByteArray) = Protobuf.bytesField(field, body)
    private fun channel(id: Int, body: ByteArray) = len(1, v(1, id.toLong()) + body)

    /** A cluster service on channel 10 with these NavigationStatusService fields. */
    private fun cluster(fields: ByteArray) = channel(10, len(8, fields))

    private fun mg(): ByteArray =
        // Its Bluetooth service sits on channel 8 (service field 6): channel 8 isn't field 8.
        channel(8, len(6, Protobuf.stringField(1, "car") + len(2, byteArrayOf(2, 3, 4)))) +
            channel(9, len(9, ByteArray(0))) +
            cluster(v(1, 1000) + v(2, 1) + len(3, v(1, 720) + v(2, 1920) + v(3, 32))) +
            channel(12, len(10, ByteArray(0))) +
            Protobuf.stringField(3, "ZS32 Color Radio")

    @Test
    fun `the MG's cluster wants each turn as a 1920x720 picture, a second apart`() {
        val spec = ClusterSpec.from(mg())!!
        assertEquals(10, spec.channel)
        assertEquals(1000, spec.minIntervalMs)
        assertEquals(ClusterSpec.IMAGE, spec.type)
        // Height first, then width: 720 then 1920, the shape of its 1920x720 screen.
        assertEquals(1920, spec.imageWidth)
        assertEquals(720, spec.imageHeight)
        assertEquals(32, spec.colourDepthBits)
        assertEquals(1920 to 720, spec.pictureSize())
        assertEquals(1000L, spec.intervalMs)
        assertFalse(spec.opaque)
        assertTrue(spec.describe(), "1920x720" in spec.describe())
    }

    @Test
    fun `a cluster that takes codes gets no pictures, whatever sizes it names`() {
        val spec = ClusterSpec.from(cluster(v(1, 0) + v(2, 2) + len(3, v(1, 256) + v(2, 256) + v(3, 16))))!!
        assertEquals(ClusterSpec.ENUM, spec.type)
        assertNull(spec.pictureSize())
    }

    @Test
    fun `a car that says nothing gets codes, at the pace myCar always kept`() {
        val spec = ClusterSpec.from(cluster(ByteArray(0)))!!
        assertEquals(10, spec.channel)
        assertEquals(0, spec.type)
        assertNull(spec.pictureSize())
        assertEquals(ClusterSpec.FLOOR_MS, spec.intervalMs)
    }

    @Test
    fun `no car gets messages faster than before`() {
        assertEquals(500L, ClusterSpec(1, minIntervalMs = 100).intervalMs)
        assertEquals(500L, ClusterSpec(1, minIntervalMs = 0).intervalMs)
        assertEquals(1500L, ClusterSpec(1, minIntervalMs = 1500).intervalMs)
    }

    @Test
    fun `a negative interval counts as none`() {
        val spec = ClusterSpec.from(cluster(v(1, -5) + v(2, 2)))!!
        assertEquals(0, spec.minIntervalMs)
        assertEquals(ClusterSpec.FLOOR_MS, spec.intervalMs)
    }

    @Test
    fun `pictures without a usable size are left out, as Android Auto does`() {
        assertNull(ClusterSpec.from(cluster(v(1, 1000) + v(2, 1)))!!.pictureSize())
        assertNull(ClusterSpec.from(cluster(v(2, 1) + len(3, v(1, 0) + v(2, 1920) + v(3, 32))))!!.pictureSize())
        assertNull(ClusterSpec.from(cluster(v(2, 1) + len(3, v(1, -720) + v(2, 1920) + v(3, 32))))!!.pictureSize())
    }

    @Test
    fun `an absurd size keeps its shape within Full HD`() {
        val (w, h) = ClusterSpec(1, type = ClusterSpec.IMAGE, imageWidth = 8000, imageHeight = 8000).pictureSize()!!
        assertEquals(w, h)
        assertTrue("$w", w in 1430..1440)
        val (wide, low) = ClusterSpec(1, type = ClusterSpec.IMAGE, imageWidth = 4000, imageHeight = 1000).pictureSize()!!
        assertTrue("${wide}x$low", wide.toLong() * low <= ClusterSpec.MAX_PIXELS)
        assertEquals(4.0, wide.toDouble() / low, 0.01)
        // A small one stays as asked.
        assertEquals(256 to 128, ClusterSpec(1, type = ClusterSpec.IMAGE, imageWidth = 256, imageHeight = 128).pictureSize())
    }

    @Test
    fun `fewer than 32 bits of colour get a black ground`() {
        assertTrue(ClusterSpec(1, colourDepthBits = 16).opaque)
        assertTrue(ClusterSpec(1, colourDepthBits = 24).opaque)
        assertFalse(ClusterSpec(1, colourDepthBits = 32).opaque)
        assertFalse(ClusterSpec(1, colourDepthBits = 0).opaque)
    }

    @Test
    fun `no cluster service, no cluster`() {
        assertNull(ClusterSpec.from(channel(9, len(9, ByteArray(0))) + channel(8, len(6, Protobuf.stringField(1, "car")))))
        assertNull(ClusterSpec.from(ByteArray(0)))
    }
}
