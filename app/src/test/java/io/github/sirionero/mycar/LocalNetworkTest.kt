package io.github.sirionero.mycar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {

    @Test
    fun homeAddressesAreLocal() {
        listOf(
            "http://192.168.1.10:8123",
            "http://10.0.2.2:8095",
            "http://172.20.0.5:8123/",
            "http://169.254.10.1",
            "http://127.0.0.1:8123",
            "http://homeassistant.local:8123",
            "http://HomeAssistant.Local.:8095",
            "http://homeassistant:8123",
            "http://[fd00::12]:8123",
            "http://[fe80::1]:8123",
            "  http://192.168.0.2:8095  ",
        ).forEach { assertTrue(it, LocalNetwork.isLocal(it)) }
    }

    @Test
    fun internetAddressesAreNot() {
        listOf(
            "https://example.ui.nabu.casa",
            "https://music.example.com",
            "http://8.8.8.8:8123",
            "http://172.32.0.1",
            "http://192.169.1.1",
            "http://100.101.102.103:8123",
            "http://[2001:db8::1]:8123",
            "http://1.2.3",
            "",
            "not an address",
        ).forEach { assertFalse(it, LocalNetwork.isLocal(it)) }
    }
}
