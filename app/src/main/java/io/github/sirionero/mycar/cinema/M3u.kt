package io.github.sirionero.mycar.cinema

import java.net.URI

/** A TV channel from an IPTV list: its name, where it streams from, its logo and its group. */
data class TvChannel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    /** HTTP headers the list asks the player to send (a user agent, a referrer). */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * IPTV lists in the M3U format providers and public lists use:
 *
 *     #EXTM3U
 *     #EXTINF:-1 tvg-id="..." tvg-logo="https://..." group-title="News",Channel name
 *     #EXTVLCOPT:http-user-agent=...
 *     https://example.com/live/stream.m3u8
 *
 * Also a group on a line of its own (#EXTGRP), headers after a "|" in the address (Kodi's way), a
 * plain list of addresses with no #EXTINF, and addresses relative to the list's own. Channels
 * Android's player can't play are left out: protected ones (a DRM licence in #KODIPROP) and those
 * on other schemes than http, https and rtsp (udp multicast, rtmp).
 */
object M3u {

    /** The channels in [text], a list downloaded from [base] (for relative addresses), up to [max]. */
    fun parse(text: String, base: String? = null, max: Int = MAX_CHANNELS): List<TvChannel> {
        val channels = mutableListOf<TvChannel>()
        var info: String? = null
        var group: String? = null
        val headers = linkedMapOf<String, String>()
        var protected = false
        // Whatever comes between two addresses is about the second one: lists put #KODIPROP and
        // #EXTVLCOPT before #EXTINF as often as after it.
        for (raw in text.removePrefix("﻿").lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> info = line.substringAfter(':', "")
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim().ifEmpty { null }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> option(line.substringAfter(':'))?.let { (k, v) -> headers[k] = v }
                line.startsWith("#KODIPROP:", ignoreCase = true) ->
                    if (line.substringAfter(':').trim().startsWith("inputstream.adaptive.license_type", ignoreCase = true)) protected = true
                line.startsWith("#") -> Unit
                else -> {
                    val channel = channel(line, info, group, headers, base)
                    if (channel != null && !protected) channels += channel
                    info = null
                    group = null
                    headers.clear()
                    protected = false
                    if (channels.size >= max) break
                }
            }
        }
        return channels
    }

    private fun channel(line: String, info: String?, group: String?, options: Map<String, String>, base: String?): TvChannel? {
        // Kodi's way: "address|User-Agent=...&Referer=...".
        val address = line.substringBefore('|').trim()
        val headers = LinkedHashMap(options)
        if ('|' in line) {
            line.substringAfter('|').split('&').forEach { pair ->
                val key = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (key.isNotEmpty() && value.isNotEmpty()) headers[headerName(key)] = decoded(value)
            }
        }
        val url = resolve(base, address) ?: return null
        val scheme = url.substringBefore(':').lowercase()
        if (scheme !in PLAYABLE) return null
        val attributes = info?.let { attributes(it) }.orEmpty()
        val name = info?.let { title(it) }?.ifEmpty { null }
            ?: attributes["tvg-name"]?.ifEmpty { null }
            ?: url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifEmpty { url }
        return TvChannel(
            name = name,
            url = url,
            logo = attributes["tvg-logo"]?.let { resolve(base, it) }?.takeIf { it.startsWith("http") },
            group = attributes["group-title"]?.trim()?.ifEmpty { null } ?: group,
            headers = headers,
        )
    }

    /** The name after #EXTINF's attributes: what follows the first comma outside quotes. */
    private fun title(info: String): String {
        var quoted = false
        for ((i, c) in info.withIndex()) {
            if (c == '"') quoted = !quoted
            if (c == ',' && !quoted) return info.substring(i + 1).trim()
        }
        return ""
    }

    private fun attributes(info: String): Map<String, String> {
        val head = info.substring(0, info.length - title(info).length).trimEnd()
        return ATTRIBUTE.findAll(head).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
    }

    /** A VLC option for the player, as a header: http-user-agent and http-referrer. */
    private fun option(option: String): Pair<String, String>? {
        val key = option.substringBefore('=').trim().lowercase()
        val value = option.substringAfter('=', "").trim().ifEmpty { return null }
        return when (key) {
            "http-user-agent" -> "User-Agent" to value
            "http-referrer", "http-referer" -> "Referer" to value
            "http-origin" -> "Origin" to value
            else -> null
        }
    }

    private fun headerName(key: String): String = when (key.lowercase()) {
        "user-agent" -> "User-Agent"
        "referer", "referrer" -> "Referer"
        "origin" -> "Origin"
        "cookie" -> "Cookie"
        else -> key
    }

    private fun decoded(value: String): String = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun resolve(base: String?, address: String): String? {
        if (address.isEmpty()) return null
        if (SCHEME.containsMatchIn(address)) return address
        if (base == null) return null
        return runCatching { URI(base).resolve(address.replace(" ", "%20")).toString() }.getOrNull()
    }

    private val ATTRIBUTE = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val PLAYABLE = setOf("http", "https", "rtsp")

    /** More than a car's list needs: some public lists have tens of thousands. */
    const val MAX_CHANNELS = 20_000
}
