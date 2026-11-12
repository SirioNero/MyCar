package io.github.sirionero.mycar.cinema

/**
 * What another app hands to Cinema, read from the intent's plain values (no Android here, so the JVM tests
 * it): a video for Cinema's player - "Open with", as NewPipe and its kin hand a stream to an outside player -
 * or a link for Cinema's browser ("Share"). Web addresses only, http and https: not a file, not another
 * app's content, never anything that would start something else.
 */
object OpenWith {

    sealed interface Asked

    /**
     * A video at [url] for Cinema's player, with the [title] the app gave it. [stream]: a playlist of pieces
     * (HLS), as NewPipe sends a live stream: it may have no end, so no place to start again from.
     */
    data class Video(val url: String, val title: String?, val stream: Boolean) : Asked

    /** A web page, for Cinema's browser: on its site if that's one of Cinema's (see [siteFor]). */
    data class Page(val url: String) : Asked

    /** Nothing Cinema can open. */
    data object Unusable : Asked

    const val VIEW = "android.intent.action.VIEW"
    const val SEND = "android.intent.action.SEND"

    /**
     * "Open with" ([VIEW]): [data] the video's address, [type] its kind. "Share" ([SEND]): the first web
     * address in [text], a page - or a video, when it's a video file's. [title]: the name the app gave it.
     */
    fun read(action: String?, data: String?, type: String?, text: String?, title: String?): Asked = when (action) {
        VIEW -> viewed(data, type, title)
        SEND -> shared(text, title)
        else -> Unusable
    }

    private fun viewed(data: String?, type: String?, title: String?): Asked {
        val url = web(data) ?: return Unusable
        val kind = type?.substringBefore(';')?.trim()?.lowercase()?.ifEmpty { null }
        val stream = kind in STREAMS || extension(url) == "m3u8"
        // DASH and the other playlists Android's player doesn't play, audio alone, a web page: not here.
        val video = if (kind == null) extension(url) in VIDEO_FILES else kind.startsWith("video/") || kind in STREAMS
        return if (video) Video(url, clean(title), stream) else Unusable
    }

    private fun shared(text: String?, title: String?): Asked {
        val url = firstLink(text) ?: return Unusable
        val extension = extension(url)
        return if (extension in VIDEO_FILES) Video(url, clean(title), stream = extension == "m3u8") else Page(url)
    }

    /** The first web address in [text], without the punctuation that closes the sentence around it. */
    internal fun firstLink(text: String?): String? {
        text ?: return null
        for (match in LINK.findAll(text)) {
            var link = match.value.trimEnd(*CLOSING)
            // A bracket closes the address only when the address didn't open one ("…/Foo_(bar)").
            while (link.endsWith(')') && link.count { it == ')' } > link.count { it == '(' }) {
                link = link.dropLast(1).trimEnd(*CLOSING)
            }
            web(link)?.let { return it }
        }
        return null
    }

    /** [raw] if it's a web address (http or https, with a host), within reason; null if not. */
    fun web(raw: String?): String? {
        val url = raw?.trim() ?: return null
        if (url.isEmpty() || url.length > MAX_URL || url.any { it.isWhitespace() || it.isISOControl() }) return null
        val match = WEB.matchEntire(url) ?: return null
        return url.takeIf { hostIn(match.groupValues[2]) != null }
    }

    /** [url]'s host, lower case: what's after any "user@" and before the port. Null if it has none. */
    fun hostOf(url: String): String? = WEB.matchEntire(url)?.groupValues?.get(2)?.let(::hostIn)

    private fun hostIn(authority: String): String? {
        val server = authority.substringAfterLast('@')
        val host = if (server.startsWith('[')) server.substringBefore(']') + "]" else server.substringBefore(':')
        return host.lowercase().trimEnd('.').takeIf { it.isNotEmpty() && it != "[]" }
    }

    /**
     * Where [url] leads, as the driver reads it before saying yes: its host and port, never what may come
     * before them ("user:password@"). Null with none.
     */
    fun serverOf(url: String): String? {
        val server = WEB.matchEntire(url)?.groupValues?.get(2)?.substringAfterLast('@') ?: return null
        return server.lowercase().takeIf { hostIn(it) != null }
    }

    /**
     * Whether [host] (as [hostOf] gives it) may lead into the phone or the home network although it names none
     * of the ranges LocalNetwork.isLocal knows: 0.0.0.0/8 (this phone), any address written in IPv6 (its many
     * spellings - "::", 0:0:0:0:0:0:0:1, ::ffff:192.168.1.1 - aren't worth telling apart here: a stream's
     * address has a name), or anything that isn't a plain name or address, which a player might still read as
     * one of those.
     */
    fun oddHost(host: String): Boolean {
        if (host.startsWith('[') || !PLAIN_HOST.matches(host)) return true
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) } && parts[0].all { it == '0' }
    }

    /** A host as Cinema names a site's (CinemaSite.host): without "www." and "m.". */
    fun bare(host: String): String = host.removePrefix("www.").removePrefix("m.")

    /** What a page that isn't on any of Cinema's sites is called in its bar: its host. */
    fun nameFor(url: String): String = hostOf(url)?.let(::bare) ?: url

    /**
     * The site of [sites] (at its address, [address]) that [url] belongs to: the same host, or one under it
     * (clips.twitch.tv is Twitch's); youtu.be is YouTube's. Null when it's on none of them.
     */
    fun <T> siteFor(url: String, sites: List<T>, address: (T) -> String): T? {
        val host = hostOf(url)?.let(::bare) ?: return null
        val wanted = SAME_SITE[host] ?: host
        return sites.firstOrNull { site ->
            val own = hostOf(address(site))?.let(::bare)
            own != null && (wanted == own || wanted.endsWith(".$own"))
        }
    }

    /** The extension of the last piece of [url]'s path, lower case; "" with none. */
    internal fun extension(url: String): String {
        val path = url.substringAfter("://").substringBefore('#').substringBefore('?').substringAfter('/', "")
        return path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    }

    /** The app's name for a video, on one line and not too long; null with none. */
    private fun clean(title: String?): String? =
        title?.replace(SPACES, " ")?.trim()?.take(MAX_TITLE)?.trim()?.ifEmpty { null }

    /** Playlists of pieces (HLS) Android's player plays: NewPipe sends a live stream as the first. */
    private val STREAMS = setOf("application/x-mpegurl", "application/vnd.apple.mpegurl")

    /** Video files and playlists by their extension, for an address sent without its kind, or shared as a link. */
    private val VIDEO_FILES = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "m3u8")

    /** Short addresses of a site in Cinema's list, and the site's own. */
    private val SAME_SITE = mapOf(
        "youtu.be" to "youtube.com",
        "youtube-nocookie.com" to "youtube.com",
        "dai.ly" to "dailymotion.com",
    )

    private val WEB = Regex("""^(https?)://([^/?#\\\s]+)(?:[/?#\\].*)?$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val LINK = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val CLOSING = charArrayOf('.', ',', ';', ':', '!', '?', '»', '”', '’', ']', '}', '>')
    private val SPACES = Regex("""\s+""")
    /** A name or an IPv4 address as a host is written plainly: letters, digits, dots and dashes. */
    private val PLAIN_HOST = Regex("""[a-z0-9.-]+""")

    /** Longer than this isn't an address a player or a page gets (a stream's runs to a couple of thousand). */
    private const val MAX_URL = 16_384
    private const val MAX_TITLE = 200
}
