package io.github.sirionero.mycar

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionLifecycleTest {
    private val version = message(1, byteArrayOf(0, 1, 0, 3))

    /** The status texts on Home come from res/values. */
    @Before fun words() {
        TestWords.install()
    }

    private fun message(id: Int, body: ByteArray = byteArrayOf()) = Frames.build(
        Frames.CHANNEL_CONTROL, false, byteArrayOf(0, id.toByte()) + body,
    )

    private fun hello(): ByteArray {
        val client = SSLContext.getDefault().createSSLEngine().apply {
            useClientMode = true
            enabledProtocols = arrayOf("TLSv1.2")
            beginHandshake()
        }
        val packet = ByteBuffer.allocate(client.session.packetBufferSize)
        client.wrap(ByteBuffer.allocate(0), packet)
        return message(3, packet.array().copyOf(packet.position()))
    }

    private fun identity(): CertProvider.Identity {
        val generated = SelfSignedCert.generate()
        return CertProvider.Identity(
            generated.keyStore, generated.certificate, SelfSignedCert.PASSWORD, "generated test identity",
        )
    }

    private fun runner(input: InputStream) = MyCarRunner(input, ByteArrayOutputStream(), ::identity)

    @Test fun `rejected authentication ignores subsequent success and service discovery in the same read`() {
        for (status in listOf(-2, -3)) {
            runner(ByteArrayInputStream(
                version + hello() + message(4, Protobuf.varintField(1, status.toLong())) +
                    message(4, Protobuf.varintField(1, 0)) + message(6) + version,
            )).run()
            assertEquals(
                if (status == -2) SessionReport.Category.CERTIFICATE else SessionReport.Category.AUTH,
                SessionReport.category,
            )
            assertEquals(SessionStatus.Phase.FAILED, SessionStatus.state.value.phase)
            assertEquals(Words.get(SessionStatus.CERT_TURNED_DOWN), SessionStatus.state.value.headline)
            assertTrue(SessionReport.stage < SessionReport.Stage.ACCEPTED)
        }
    }

    @Test fun `TLS failure ignores later protocol messages`() {
        val fatalAlert = message(3, byteArrayOf(21, 3, 3, 0, 2, 2, 42))
        runner(ByteArrayInputStream(version + hello() + fatalAlert + version + message(6))).run()
        assertEquals(SessionReport.Category.TLS, SessionReport.category)
        assertEquals(Words.get(SessionStatus.CERT_TURNED_DOWN), SessionStatus.state.value.headline)
        assertTrue(SessionReport.stage < SessionReport.Stage.ACCEPTED)
    }

    /** What the phone wrote, frame by frame. */
    private fun replies(output: ByteArrayOutputStream): List<Frames.Frame> {
        val bytes = output.toByteArray()
        val parser = Frames.Parser().apply { append(bytes, bytes.size) }
        return generateSequence { parser.next() }.toList()
    }

    private fun byeByeReply(output: ByteArrayOutputStream) =
        replies(output).firstOrNull { it.payload.contentEquals(byteArrayOf(0, 16)) }

    /** Gives [bytes], then holds the end of the stream until [until] (two seconds at most), as a car waiting for an answer. */
    private class SlowEnd(bytes: ByteArray, val until: () -> Boolean) : ByteArrayInputStream(bytes) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = super.read(b, off, len)
            if (read >= 0) return read
            val deadline = System.currentTimeMillis() + 2_000
            while (!until() && System.currentTimeMillis() < deadline) Thread.sleep(10)
            return -1
        }
    }

    @Test fun `a ByeBye during setup is answered in the clear, and the hang-up after it still counts`() {
        val output = ByteArrayOutputStream()
        val bye = message(15, Protobuf.varintField(1, 3))
        MyCarRunner(SlowEnd(version + hello() + bye) { byeByeReply(output) != null }, output, ::identity).run()
        val reply = byeByeReply(output)
        assertNotNull(reply)
        assertFalse(reply!!.encrypted)
        assertEquals(SessionReport.Category.BYEBYE, SessionReport.category)
        // The car hung up while checking the certificate: the driver reads it as before.
        assertEquals("The car hung up during setup", SessionStatus.state.value.headline)
    }

    @Test fun `a ByeBye during a drive is answered and ends the session`() {
        val runner = MyCarRunner(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), ::identity)
        val identity = identity()
        setField(runner, "identityTask", FutureTask { PhoneTls(identity.keyStore, identity.password) }.apply { run() })
        val state = MyCarRunner::class.java.getDeclaredField("state").apply { isAccessible = true }
        state.set(runner, state.type.enumConstants.first { (it as Enum<*>).name == "DONE" })
        try {
            MyCarRunner::class.java.getDeclaredMethod("onByeByeRequest", ByteArray::class.java)
                .apply { isAccessible = true }.invoke(runner, Protobuf.varintField(1, 7))
            assertFalse(getField(runner, "running") as Boolean)
            assertTrue(getField(runner, "drainWriterOnExit") as Boolean)
        } finally {
            runner.stop()
        }
    }

    @Test fun `ping echoes the full timestamp and the data it carries`() {
        for (data in listOf(null, byteArrayOf(), byteArrayOf(0, -1, 42))) {
            val output = ByteArrayOutputStream()
            val runner = MyCarRunner(ByteArrayInputStream(byteArrayOf()), output, ::identity)
            val identity = identity()
            setField(runner, "identityTask", FutureTask { PhoneTls(identity.keyStore, identity.password) }.apply { run() })
            val timestamp = 0x1234_5678_9abcL
            val request = Protobuf.varintField(1, timestamp) + Protobuf.varintField(2, 1) +
                (data?.let { Protobuf.bytesField(3, it) } ?: byteArrayOf())
            try {
                MyCarRunner::class.java.getDeclaredMethod("onPingRequest", ByteArray::class.java)
                    .apply { isAccessible = true }.invoke(runner, request)
                val writer = getField(runner, "writer") as ExecutorService
                writer.shutdown()
                assertTrue(writer.awaitTermination(2, TimeUnit.SECONDS))
                val response = replies(output).single().payload
                assertEquals(12, response[1].toInt())
                val fields = Wire.fields(response.copyOfRange(2, response.size))
                assertEquals(timestamp, Wire.varint(fields, 1))
                if (data == null) assertNull(Wire.bytes(fields, 2)) else assertArrayEquals(data, Wire.bytes(fields, 2))
            } finally {
                runner.stop()
            }
        }
    }

    private fun getField(runner: MyCarRunner, name: String): Any? =
        MyCarRunner::class.java.getDeclaredField(name).apply { isAccessible = true }.get(runner)

    private fun setField(runner: MyCarRunner, name: String, value: Any) {
        MyCarRunner::class.java.getDeclaredField(name).apply { isAccessible = true }.set(runner, value)
    }

    @Test fun `authentication success before TLS completes is ignored`() {
        runner(ByteArrayInputStream(version + message(4, Protobuf.varintField(1, 0)))).run()
        assertEquals(SessionReport.Category.TLS, SessionReport.category)
        assertEquals(SessionReport.Stage.VERSIONS, SessionReport.stage)
    }

    @Test fun `stop followed by EOF does not report a handshake failure`() {
        stopDuringRead { -1 }
    }

    @Test fun `stop followed by a read exception does not report a USB failure`() {
        stopDuringRead { throw IOException("transport closed by owner") }
    }

    @Test fun `stop ignores bytes returned by an in-flight read`() {
        stopDuringRead { bytes -> version.copyInto(bytes); version.size }
    }

    private fun stopDuringRead(result: (ByteArray) -> Int) {
        lateinit var runner: MyCarRunner
        val input = object : InputStream() {
            override fun read(): Int = error("bulk reads only")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                runner.stop()
                return result(bytes)
            }
        }
        runner = runner(input)
        runner.run()
        assertEquals(SessionReport.Category.NONE, SessionReport.category)
        assertEquals(SessionStatus.Phase.DISCONNECTED, SessionStatus.state.value.phase)
    }

    @Test fun `stop while preparing TLS leaves the session disconnected`() {
        val releaseIdentity = CountDownLatch(1)
        val runner = MyCarRunner(ByteArrayInputStream(version + hello()), ByteArrayOutputStream(), {
            check(releaseIdentity.await(5, TimeUnit.SECONDS))
            identity()
        })
        val reader = Thread { runner.run() }
        reader.start()
        try {
            // The reader waits on FutureTask.get while the identity provider is held above.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (reader.state != Thread.State.WAITING && reader.isAlive && System.nanoTime() < deadline) {
                reader.join(1)
            }
            assertEquals(Thread.State.WAITING, reader.state)
            runner.stop()
        } finally {
            releaseIdentity.countDown()
            reader.join(5_000)
        }
        assertTrue("reader must finish", !reader.isAlive)
        assertEquals(SessionReport.Category.NONE, SessionReport.category)
        assertEquals(SessionStatus.Phase.DISCONNECTED, SessionStatus.state.value.phase)
    }

    private fun verdicts(input: ByteArray): List<CertVerdict> {
        val seen = mutableListOf<CertVerdict>()
        MyCarRunner(ByteArrayInputStream(input), ByteArrayOutputStream(), ::identity, onCertVerdict = { _, v -> seen += v }).run()
        return seen
    }

    @Test fun `the car's verdict on the certificate goes out once`() {
        val seen = verdicts(version + hello() + message(4, Protobuf.varintField(1, -2L)) + message(4, Protobuf.varintField(1, 0L)))
        assertEquals(listOf<CertVerdict>(CertVerdict.Refused(hard = true, why = "AuthComplete -2")), seen)
    }

    @Test fun `a TLS alert is a refusal`() {
        val fatalAlert = message(3, byteArrayOf(21, 3, 3, 0, 2, 2, 42))
        val seen = verdicts(version + hello() + fatalAlert)
        assertEquals(1, seen.size)
        assertTrue((seen.single() as CertVerdict.Refused).hard)
    }

    @Test fun `a hang-up while the car checks the certificate is a soft refusal`() {
        assertEquals(listOf<CertVerdict>(CertVerdict.Refused(hard = false, why = "dropped during TLS")), verdicts(version + hello()))
    }

    @Test fun `a hang-up before the certificate was shown says nothing of it`() {
        assertEquals(emptyList<CertVerdict>(), verdicts(version))
    }

    @Test fun `the car's date is not a refusal`() {
        for (status in listOf(-23, -24)) {
            val seen = verdicts(version + hello() + message(4, Protobuf.varintField(1, status.toLong())))
            assertEquals(listOf<CertVerdict>(CertVerdict.Clock), seen)
            assertEquals("Check the car's date", SessionStatus.state.value.headline)
        }
    }

    @Test fun `EOF and transport failure shut down the writer`() {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("test disconnect")
        }
        for (input in listOf(ByteArrayInputStream(version), failing)) {
            val runner = runner(input)
            runner.run()
            val field = MyCarRunner::class.java.getDeclaredField("writer").apply { isAccessible = true }
            assertTrue((field.get(runner) as ExecutorService).isShutdown)
        }
    }
}
