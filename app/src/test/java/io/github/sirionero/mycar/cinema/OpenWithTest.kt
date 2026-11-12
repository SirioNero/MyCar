package io.github.sirionero.mycar.cinema

import io.github.sirionero.mycar.cinema.OpenWith.Page
import io.github.sirionero.mycar.cinema.OpenWith.SEND
import io.github.sirionero.mycar.cinema.OpenWith.Unusable
import io.github.sirionero.mycar.cinema.OpenWith.VIEW
import io.github.sirionero.mycar.cinema.OpenWith.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenWithTest {

    private val stream = "https://rr3---sn-hpa7zn7s.googlevideo.com/videoplayback?expire=1762990000&itag=18&mime=video%2Fmp4&sig=AJfQdSswRQIhAKx"

    @Test
    fun aVideoAsNewPipeHandsItToAnOutsidePlayer() {
        // NavigationHelper.playOnExternalPlayer: the stream's address, its format's type, the title in EXTRA_TITLE.
        assertEquals(Video(stream, "Big Buck Bunny", stream = false), OpenWith.read(VIEW, stream, "video/mp4", null, "Big Buck Bunny"))
        assertEquals(Video(stream, null, stream = false), OpenWith.read(VIEW, stream, "video/*", null, null))
        assertEquals(Video(stream, null, stream = false), OpenWith.read(VIEW, stream, "video/webm; codecs=vp9", null, " "))
    }

    @Test
    fun aLiveStreamIsAPlaylistOfPieces() {
        // NewPipe names HLS "application/x-mpegURL", others in lower case or Apple's name.
        val live = "https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1762990000/id/x/index.m3u8"
        assertEquals(Video(live, "News", stream = true), OpenWith.read(VIEW, live, "application/x-mpegURL", null, "News"))
        assertEquals(Video(live, null, stream = true), OpenWith.read(VIEW, live, "application/vnd.apple.mpegurl", null, null))
        assertEquals(Video("http://10.0.2.2:8001/tv/live.m3u8", null, stream = true), OpenWith.read(VIEW, "http://10.0.2.2:8001/tv/live.m3u8", "video/*", null, null))
    }

    @Test
    fun whatAndroidsPlayerDoesntPlayIsLeft() {
        assertEquals(Unusable, OpenWith.read(VIEW, "https://example.com/manifest.mpd", "application/dash+xml", null, null))
        assertEquals(Unusable, OpenWith.read(VIEW, "https://example.com/stream.ism/Manifest", "application/vnd.ms-sstr+xml", null, null))
        assertEquals(Unusable, OpenWith.read(VIEW, "https://example.com/song.m4a", "audio/mp4", null, null))
        assertEquals(Unusable, OpenWith.read(VIEW, "https://example.com/page", "text/html", null, null))
    }

    @Test
    fun onlyWebAddresses() {
        for (address in listOf(
            "content://com.example.files/video.mp4", "file:///sdcard/Movies/a.mp4", "javascript:alert(1)",
            "intent://x#Intent;scheme=http;end", "rtsp://example.com/live", "ftp://example.com/a.mp4", "data:video/mp4;base64,AAAA",
            "https://", "https:///a.mp4", "http://:8080/a.mp4", "https://exa mple.com/a.mp4", null, "",
        )) {
            assertEquals(address, Unusable, OpenWith.read(VIEW, address, "video/mp4", null, null))
        }
        assertEquals(Unusable, OpenWith.read(VIEW, "https://example.com/" + "a".repeat(20_000) + ".mp4", "video/mp4", null, null))
        assertEquals(Unusable, OpenWith.read("android.intent.action.EDIT", stream, "video/mp4", null, null))
        assertEquals(Unusable, OpenWith.read(null, stream, "video/mp4", null, null))
    }

    @Test
    fun withoutAKindTheExtensionSays() {
        assertEquals(Video("https://example.com/films/Night.MKV", null, stream = false), OpenWith.read(VIEW, "https://example.com/films/Night.MKV", null, null, null))
        assertEquals(Unusable, OpenWith.read(VIEW, "https://example.com/watch?v=1", null, null, null))
        assertEquals(Unusable, OpenWith.read(VIEW, "https://example.com", null, null, null))
    }

    @Test
    fun aSharedLinkIsAPage() {
        // NewPipe's Share: the video's page in EXTRA_TEXT, its title in EXTRA_TITLE and EXTRA_SUBJECT.
        assertEquals(Page("https://www.youtube.com/watch?v=aqz-KE-bpKQ"), OpenWith.read(SEND, null, "text/plain", "https://www.youtube.com/watch?v=aqz-KE-bpKQ", "Big Buck Bunny"))
        assertEquals(Page("https://youtu.be/aqz-KE-bpKQ?t=42"), OpenWith.read(SEND, null, "text/plain", "Look at this: https://youtu.be/aqz-KE-bpKQ?t=42.", null))
        assertEquals(Page("http://10.0.2.2:8001/"), OpenWith.read(SEND, null, "text/plain", "On the runner (http://10.0.2.2:8001/)!", null))
        assertEquals(Page("https://en.wikipedia.org/wiki/Cinema_(film)"), OpenWith.read(SEND, null, "text/plain", "«https://en.wikipedia.org/wiki/Cinema_(film)».", null))
        assertEquals(Page("HTTPS://WWW.RAIPLAY.IT/dirette/rai1"), OpenWith.read(SEND, null, "text/plain", "HTTPS://WWW.RAIPLAY.IT/dirette/rai1", null))
    }

    @Test
    fun aSharedVideoFileGoesToThePlayer() {
        assertEquals(
            Video("https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4", "Big Buck Bunny", stream = false),
            OpenWith.read(SEND, null, "text/plain", "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4", "Big Buck Bunny"),
        )
    }

    @Test
    fun aSharedTextWithNoWebAddressIsNothing() {
        assertEquals(Unusable, OpenWith.read(SEND, null, "text/plain", "Meet at 8 at the cinema", null))
        assertEquals(Unusable, OpenWith.read(SEND, null, "text/plain", "ftp://example.com/a.mp4 and file:///sdcard/a.mp4", null))
        assertEquals(Unusable, OpenWith.read(SEND, null, "text/plain", null, null))
        // The first web address is taken, past one that isn't.
        assertEquals(Page("https://vimeo.com/76979871"), OpenWith.read(SEND, null, "text/plain", "https://:80/x, then https://vimeo.com/76979871", null))
    }

    @Test
    fun theTitleIsOneLineAndNotTooLong() {
        val video = OpenWith.read(VIEW, stream, "video/mp4", null, "  Big   Buck\nBunny  ") as Video
        assertEquals("Big Buck Bunny", video.title)
        assertEquals(200, (OpenWith.read(VIEW, stream, "video/mp4", null, "x".repeat(500)) as Video).title?.length)
    }

    @Test
    fun aLinkFindsItsSiteInCinema() {
        val sites = listOf("https://www.youtube.com", "https://www.raiplay.it", "https://www.twitch.tv", "http://192.168.1.20:8096/web/")
        fun site(url: String) = OpenWith.siteFor(url, sites) { it }
        assertEquals("https://www.youtube.com", site("https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
        assertEquals("https://www.youtube.com", site("https://m.youtube.com/watch?v=aqz-KE-bpKQ"))
        assertEquals("https://www.youtube.com", site("https://youtu.be/aqz-KE-bpKQ"))
        assertEquals("https://www.youtube.com", site("HTTPS://YOUTUBE.COM/shorts/x"))
        assertEquals("https://www.raiplay.it", site("https://www.raiplay.it/video/2024/01/x.html"))
        assertEquals("https://www.twitch.tv", site("https://clips.twitch.tv/SomeClip"))
        assertEquals("http://192.168.1.20:8096/web/", site("http://192.168.1.20:8096/web/#/details?id=1"))
        assertNull(site("https://notyoutube.com/watch?v=1"))
        assertNull(site("https://example.com/video"))
    }

    @Test
    fun aHostDressedAsAnotherIsntTakenForIt() {
        val sites = listOf("https://www.youtube.com")
        // The host is what's after the last "@", and a backslash ends it as it does in a browser.
        assertNull(OpenWith.siteFor("https://www.youtube.com@evil.example/watch", sites) { it })
        assertNull(OpenWith.siteFor("https://evil.example\\@www.youtube.com/", sites) { it })
        assertEquals("evil.example", OpenWith.hostOf("https://evil.example\\@www.youtube.com/"))
        assertEquals("[::1]", OpenWith.hostOf("http://[::1]:8080/a.mp4"))
    }

    @Test
    fun aPageOffTheListIsCalledByItsHost() {
        assertEquals("example.com", OpenWith.nameFor("https://www.example.com/news/1"))
        assertEquals("10.0.2.2", OpenWith.nameFor("http://10.0.2.2:8001/"))
    }
}
