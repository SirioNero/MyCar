package io.github.sirionero.mycar

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FramingTest {
    /** The whole plaintext length a FIRST frame carries, bytes 4-7 of its header. */
    private fun totalSize(frames: ByteArray) =
        (4..7).fold(0) { size, i -> (size shl 8) or (frames[i].toInt() and 0xff) }

    @Test fun `fragmentation survives every byte arriving separately`() {
        val message = ByteArray(40_000) { it.toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(3, Frames.writeMessage(output, 5, false, Frames.MESSAGE_SPECIFIC, message) { error("plaintext") })
        val parser = Frames.Parser()
        val assembler = Frames.Assembler()
        var received: ByteArray? = null
        for (byte in output.toByteArray()) {
            parser.append(byteArrayOf(byte), 1)
            parser.next()?.let { received = assembler.offer(it, it.payload) }
        }
        assertArrayEquals(message, received)
    }

    @Test fun `encrypted fragments count plaintext length and encrypt each chunk`() {
        val output = ByteArrayOutputStream()
        val message = ByteArray(20_000) { 42 }
        Frames.writeMessage(output, 1, true, Frames.MESSAGE_SPECIFIC, message) { byteArrayOf(99) + it }
        assertEquals(20_000, totalSize(output.toByteArray()))
        val parser = Frames.Parser()
        parser.append(output.toByteArray(), output.size())
        val first = parser.next()!!
        val last = parser.next()!!
        assertEquals(16_121, first.payload.size)
        assertEquals(Frames.TYPE_LAST, last.frameType)
        val assembler = Frames.Assembler()
        assertNull(assembler.offer(first, first.payload.drop(1).toByteArray()))
        assertArrayEquals(message, assembler.offer(last, last.payload.drop(1).toByteArray()))
    }

    @Test fun `fragment budget includes the short or extended header`() {
        for (size in listOf(16_124, 16_125, 32_245)) {
            val output = ByteArrayOutputStream()
            Frames.writeMessage(output, 1, false, Frames.MESSAGE_SPECIFIC, ByteArray(size)) { error("plaintext") }
            val parser = Frames.Parser()
            parser.append(output.toByteArray(), output.size())
            val first = parser.next()!!
            if (size == 16_124) {
                assertEquals(Frames.TYPE_BULK, first.frameType)
                assertEquals(16_124, first.payload.size)
            } else {
                assertEquals(Frames.TYPE_FIRST, first.frameType)
                assertEquals(size, totalSize(output.toByteArray()))
                assertEquals(16_120, first.payload.size)
                val next = parser.next()!!
                if (size == 32_245) {
                    assertEquals(Frames.TYPE_MIDDLE, next.frameType)
                    assertEquals(16_124, next.payload.size)
                    val last = parser.next()!!
                    assertEquals(Frames.TYPE_LAST, last.frameType)
                    assertEquals(1, last.payload.size)
                } else {
                    assertEquals(Frames.TYPE_LAST, next.frameType)
                    assertEquals(5, next.payload.size)
                }
            }
            assertNull(parser.next())
        }
    }

    @Test fun `a short message still goes out as one frame`() {
        val output = ByteArrayOutputStream()
        assertEquals(1, Frames.writeMessage(output, 0, false, Frames.MESSAGE_CONTROL, byteArrayOf(0, 8)) { error("plaintext") })
        assertArrayEquals(Frames.build(0, false, byteArrayOf(0, 8), Frames.MESSAGE_CONTROL), output.toByteArray())
    }
}
