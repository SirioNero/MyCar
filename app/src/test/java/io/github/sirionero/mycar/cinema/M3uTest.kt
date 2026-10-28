package io.github.sirionero.mycar.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTest {

    @Test
    fun readsTheUsualList() {
        val list = """
            #EXTM3U x-tvg-url="https://example.com/epg.xml"
            #EXTINF:-1 tvg-id="rai1.it" tvg-name="Rai 1" tvg-logo="https://example.com/rai1.png" group-title="General",Rai 1 HD
            https://example.com/rai1/index.m3u8
            #EXTINF:-1 tvg-logo="" group-title="News, Italy",News 24, live
            http://example.com:8080/news.ts
        """.trimIndent()
        val channels = M3u.parse(list)
        assertEquals(2, channels.size)
        assertEquals(TvChannel("Rai 1 HD", "https://example.com/rai1/index.m3u8", "https://example.com/rai1.png", "General"), channels[0])
        assertEquals("News 24, live", channels[1].name)
        assertEquals("News, Italy", channels[1].group)
        assertNull(channels[1].logo)
    }

    @Test
    fun windowsLineEndsAndABomMakeNoDifference() {
        val channels = M3u.parse("﻿#EXTM3U\r\n#EXTINF:-1,One\r\nhttp://a/1\r\n#EXTINF:-1,Two\r\nhttp://a/2\r\n")
        assertEquals(listOf("One", "Two"), channels.map { it.name })
        assertEquals(listOf("http://a/1", "http://a/2"), channels.map { it.url })
    }

    @Test
    fun headersComeFromVlcOptionsAndAfterAPipe() {
        val channels = M3u.parse(
            """
            #EXTINF:-1,With options
            #EXTVLCOPT:http-user-agent=Player/1.0
            #EXTVLCOPT:http-referrer=https://site.example/
            https://cdn.example/a.m3u8
            #EXTINF:-1,Kodi style
            https://cdn.example/b.m3u8|User-Agent=Player%2F2.0&Referer=https://other.example/
            """.trimIndent(),
        )
        assertEquals(mapOf("User-Agent" to "Player/1.0", "Referer" to "https://site.example/"), channels[0].headers)
        assertEquals("https://cdn.example/b.m3u8", channels[1].url)
        assertEquals(mapOf("User-Agent" to "Player/2.0", "Referer" to "https://other.example/"), channels[1].headers)
    }

    @Test
    fun whatComesBeforeAnAddressIsAboutItOnly() {
        val channels = M3u.parse(
            """
            #EXTGRP:Sport
            #EXTVLCOPT:http-user-agent=Player/1.0
            #EXTINF:-1,First
            http://a/1
            #EXTINF:-1,Second
            http://a/2
            """.trimIndent(),
        )
        assertEquals("Sport", channels[0].group)
        assertEquals(mapOf("User-Agent" to "Player/1.0"), channels[0].headers)
        assertNull(channels[1].group)
        assertTrue(channels[1].headers.isEmpty())
    }

    @Test
    fun protectedAndUnplayableChannelsAreLeftOut() {
        val channels = M3u.parse(
            """
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://licence.example/
            #EXTINF:-1,Protected
            https://cdn.example/drm.mpd
            #EXTINF:-1,Multicast
            udp://@239.0.0.1:1234
            #EXTINF:-1,Rtmp
            rtmp://example/live
            #EXTINF:-1,Fine
            https://cdn.example/fine.m3u8
            """.trimIndent(),
        )
        assertEquals(listOf("Fine"), channels.map { it.name })
    }

    @Test
    fun aPlainListOfAddressesAndRelativeOnes() {
        val channels = M3u.parse(
            """
            http://example.com/live/one.m3u8
            #EXTINF:-1 tvg-logo="logos/two.png",Two
            two/index.m3u8
            """.trimIndent(),
            base = "http://192.168.1.10:9981/playlist/channels.m3u",
        )
        assertEquals("one.m3u8", channels[0].name)
        assertEquals("http://192.168.1.10:9981/playlist/two/index.m3u8", channels[1].url)
        assertEquals("http://192.168.1.10:9981/playlist/logos/two.png", channels[1].logo)
        // Without the list's own address there's nothing to resolve against.
        assertEquals(1, M3u.parse("two/index.m3u8\nhttp://a/b").size)
    }

    @Test
    fun aNameFromTheAttributesWhenTheTitleIsMissing() {
        val channels = M3u.parse("#EXTINF:-1 tvg-name=\"Named\"\nhttp://a/x.m3u8\n#EXTINF:-1,\nhttp://a/y.ts")
        assertEquals(listOf("Named", "y.ts"), channels.map { it.name })
    }

    @Test
    fun hugeListsStopAtTheLimit() {
        val list = (1..50).joinToString("\n") { "#EXTINF:-1,Channel $it\nhttp://a/$it" }
        assertEquals(10, M3u.parse(list, max = 10).size)
    }
}
