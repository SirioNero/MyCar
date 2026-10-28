package io.github.sirionero.mycar

import io.github.sirionero.mycar.home.HaException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TailnetTest {

    @Test
    fun tailnetAddressesAndNames() {
        listOf(
            "http://umbrel:8095",
            "http://Umbrel.:8123",
            "http://umbrel.tail1234.ts.net:8123",
            "http://100.64.0.1:8123",
            "http://100.101.102.103",
            "http://100.127.255.254",
            "http://[fd7a:115c:a1e0::1]:8095",
        ).forEach { assertTrue(it, Tailnet.looksTailnet(it)) }
        assertTrue(Tailnet.looksTailnet("http://ha.tailnet.test:8123", listOf("tailnet.test")))
    }

    @Test
    fun otherAddressesAreNot() {
        listOf(
            "https://example.ui.nabu.casa",
            "http://192.168.1.10:8123",
            "http://100.63.255.255",
            "http://100.128.0.1",
            "http://[fd00::12]:8123",
            "http://ha.tailnet.test:8123",
            "",
            "not an address",
        ).forEach { assertFalse(it, Tailnet.looksTailnet(it)) }
    }

    @Test
    fun tailnetAddressesAreNotTheHomeNetwork() {
        assertFalse(LocalNetwork.isLocal("http://[fd7a:115c:a1e0::1]:8095"))
        assertFalse(LocalNetwork.isLocal("http://100.64.0.1:8123"))
        assertTrue(LocalNetwork.isLocal("http://umbrel:8095"))
    }

    @Test
    fun failuresFromExceptions() {
        assertEquals(Tailnet.Failure.NO_SUCH_HOST, Tailnet.failureOf(UnknownHostException("Unable to resolve host \"umbrel\": No address associated with hostname")))
        assertEquals(Tailnet.Failure.TIMEOUT, Tailnet.failureOf(SocketTimeoutException("connect timed out")))
        assertEquals(Tailnet.Failure.UNREACHABLE, Tailnet.failureOf(ConnectException("Failed to connect to /100.64.0.1:8123")))
        assertEquals(Tailnet.Failure.NO_SUCH_HOST, Tailnet.failureOf(RuntimeException("wrapped", UnknownHostException("umbrel"))))
        assertEquals(Tailnet.Failure.OTHER, Tailnet.failureOf(IllegalStateException("The token isn't valid")))
        assertEquals(Tailnet.Failure.OTHER, Tailnet.failureOf(null as Throwable?))
    }

    @Test
    fun failuresFromWords() {
        assertEquals(Tailnet.Failure.NO_SUCH_HOST, Tailnet.failureOf("Unable to resolve host \"umbrel\": No address associated with hostname"))
        assertEquals(Tailnet.Failure.TIMEOUT, Tailnet.failureOf("timeout"))
        assertEquals(Tailnet.Failure.OTHER, Tailnet.failureOf("Access denied"))
    }

    @Test
    fun homeAssistantFailures() {
        val lookup = HaException("Unable to resolve host \"umbrel\"", HaException.Reason.NETWORK, UnknownHostException("umbrel"))
        assertEquals(Tailnet.Failure.NO_SUCH_HOST, HaException.failureOf(lookup))
        assertEquals(Tailnet.Failure.TIMEOUT, HaException.failureOf(HaException("No answer from x", HaException.Reason.NO_RESPONSE)))
        assertEquals(Tailnet.Failure.OTHER, HaException.failureOf(HaException("The token isn't valid", HaException.Reason.INVALID_TOKEN)))
    }

    @Test
    fun aNameIsTailscalesOnlyBehindTailscale() {
        // Behind Tailscale: MagicDNS's names, *.ts.net, the search domain's, and the addresses.
        assertTrue(Tailnet.onTailnetBehind("http://homeassistant:8123", tailscale = true, searchDomains = emptyList()))
        assertTrue(Tailnet.onTailnetBehind("http://ha.tailnet.test:8123", tailscale = true, searchDomains = listOf("tailnet.test")))
        // Behind another VPN that leaves myCar out, a name without dots is the router's.
        assertFalse(Tailnet.onTailnetBehind("http://homeassistant:8123", tailscale = false, searchDomains = emptyList()))
        assertFalse(Tailnet.onTailnetBehind("http://ha.home.lan:8123", tailscale = false, searchDomains = listOf("home.lan")))
        // A tailnet address is one whatever the VPN.
        assertTrue(Tailnet.onTailnetBehind("http://100.101.102.103:8123", tailscale = false, searchDomains = emptyList()))
        assertTrue(Tailnet.onTailnetBehind("http://[fd7a:115c:a1e0::1]:8095", tailscale = false, searchDomains = emptyList()))
    }
}
