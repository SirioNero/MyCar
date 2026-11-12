package io.github.sirionero.mycar.wireless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When to tell the driver that a VPN keeps myCar off the car's Wi-Fi. */
class VpnBlockTest {

    // Android's words for a failed connect, as the tries log them.
    private val refused = "failed to connect to /192.168.1.101 (port 5277) from /10.64.0.2 (port 40112) after 5000ms: isConnected failed: ECONNREFUSED (Connection refused)"
    private val timedOut = "failed to connect to /192.168.1.101 (port 5277) from /:: (port 0) after 5000ms"
    private val unreachable = "failed to connect to /192.168.1.101 (port 5277) from /:: (port 0) after 5000ms: connect failed: ENETUNREACH (Network is unreachable)"
    private val denied = "failed to connect to /192.168.1.101 (port 5277) from /:: (port 0) after 5000ms: connect failed: EACCES (Permission denied)"

    @Test
    fun `no refused bind - never said`() {
        // myCar left out of the VPN, as for the user's cars: the bind to the car's network works.
        assertFalse(VpnBlock.due(sinceRefusedMs = null, unanswered = 50, answered = false, said = false))
    }

    @Test
    fun `said after 10 s and two tries the car didn't answer`() {
        assertFalse(VpnBlock.due(sinceRefusedMs = 9_999, unanswered = 5, answered = false, said = false))
        assertFalse(VpnBlock.due(sinceRefusedMs = 30_000, unanswered = 1, answered = false, said = false))
        assertTrue(VpnBlock.due(sinceRefusedMs = 10_000, unanswered = 2, answered = false, said = false))
    }

    @Test
    fun `once per start-up`() {
        assertFalse(VpnBlock.due(sinceRefusedMs = 40_000, unanswered = 20, answered = false, said = true))
    }

    @Test
    fun `the car answering on the ordinary route - nothing said, it works`() {
        assertFalse(VpnBlock.due(sinceRefusedMs = 40_000, unanswered = 20, answered = true, said = false))
    }

    @Test
    fun `tries counted over the car's joins`() {
        // An adapter that asks for its Wi-Fi again every few seconds: each join starts the tries anew,
        // its first try refused again (EPERM) and then one over the ordinary route, 5 s to time out.
        var unanswered = 0
        val firstRefusal = 0L
        val joins = listOf(0L, 8_000L, 16_000L)
        var saidAt: Long? = null
        for (join in joins) {
            val failedAt = join + 1_000 + 5_000
            unanswered++
            if (saidAt == null && VpnBlock.due(failedAt - firstRefusal, unanswered, answered = false, said = false)) saidAt = failedAt
        }
        // The second join's first try over the ordinary route: 14 s after the first refusal.
        assertEquals(14_000L, saidAt)
    }

    @Test
    fun `a refusal is the car's only with its Wi-Fi among the phone's`() {
        assertTrue(VpnBlock.answered(refused, carWifiOnPhone = true))
        // Through the VPN, or the phone's main network that isn't the car's: the VPN's own stack, or a
        // device at home with the same address, refused it.
        assertFalse(VpnBlock.answered(refused, carWifiOnPhone = false))
    }

    @Test
    fun `timeouts, unreachable networks and denied permissions never reached the car`() {
        for (error in listOf(timedOut, unreachable, denied, null)) {
            assertFalse(VpnBlock.answered(error, carWifiOnPhone = true))
            assertFalse(VpnBlock.answered(error, carWifiOnPhone = false))
        }
    }
}
