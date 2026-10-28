package io.github.sirionero.mycar.cinema

import android.content.Context
import io.github.sirionero.mycar.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** A video site in Cinema: its name, where it starts, and whether it's asked for as on a computer. */
data class CinemaSite(val name: String, val url: String, val desktop: Boolean = false) {
    /** The site's host without "www.", as its icon and the protected-video list know it. */
    val host: String
        get() = runCatching { URI(url).host }.getOrNull().orEmpty().lowercase().removePrefix("www.").removePrefix("m.")
}

/**
 * Cinema, myCar's video app: the driver's video sites, chosen on the phone, each opened in a
 * browser made for video (see car/CinemaApp.kt). No site's logo ships with myCar: each tile shows
 * the icon the site gives itself, fetched like any browser's (see [SiteIcons]).
 *
 * Video shows only with the car parked, unless the car's owner turns that off on the phone, after
 * a warning that it's discouraged and in many countries against the law ([parkedOnly]).
 */
object Cinema {

    private const val KEY_SITES = "cinema_sites"
    private const val KEY_PARKED_ONLY = "cinema_parked_only"

    /** To start with, until the driver changes the list: sites whose video shows in the car. */
    val SUGGESTED = listOf(
        CinemaSite("YouTube", "https://www.youtube.com"),
        CinemaSite("RaiPlay", "https://www.raiplay.it"),
        CinemaSite("Twitch", "https://www.twitch.tv"),
    )

    /**
     * More sites, a tap away on the phone: free to watch, and their video isn't protected, so it
     * shows in the car.
     */
    val MORE = listOf(
        CinemaSite("Dailymotion", "https://www.dailymotion.com"),
        CinemaSite("Vimeo", "https://vimeo.com"),
        CinemaSite("La7", "https://www.la7.it"),
        CinemaSite("TED", "https://www.ted.com"),
        CinemaSite("Arte", "https://www.arte.tv"),
        CinemaSite("NASA+", "https://plus.nasa.gov"),
        CinemaSite("Internet Archive", "https://archive.org/details/feature_films"),
    )

    /**
     * Sites whose video is protected (DRM): Android hides protected video from a screen made into
     * a video stream, as the car's is, so their picture most likely stays black in the car.
     */
    private val PROTECTED = setOf(
        "netflix.com", "primevideo.com", "disneyplus.com", "dazn.com", "nowtv.it", "skygo.sky.it",
        "paramountplus.com", "max.com", "skyshowtime.com", "timvision.it", "discoveryplus.com", "tv.apple.com",
    )

    private var app: Context? = null
    private val sitesFlow = MutableStateFlow(SUGGESTED)
    private val parkedOnlyFlow = MutableStateFlow(true)

    val sites: StateFlow<List<CinemaSite>> = sitesFlow.asStateFlow()

    /** Video only with the car parked: on unless the owner turned it off on the phone. */
    val parkedOnly: StateFlow<Boolean> = parkedOnlyFlow.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        val saved = AppSettings.getString(app, KEY_SITES, "")
        sitesFlow.value = if (saved.isEmpty()) SUGGESTED else read(saved)
        parkedOnlyFlow.value = AppSettings.getString(app, KEY_PARKED_ONLY, "true") == "true"
        TvLists.init(app)
    }

    fun add(site: CinemaSite) = save(sitesFlow.value.filterNot { it.url == site.url } + site)

    fun remove(site: CinemaSite) = save(sitesFlow.value.filterNot { it.url == site.url })

    fun setDesktop(site: CinemaSite, desktop: Boolean) =
        save(sitesFlow.value.map { if (it.url == site.url) it.copy(desktop = desktop) else it })

    fun backToSuggested() = save(SUGGESTED)

    fun setParkedOnly(on: Boolean) {
        parkedOnlyFlow.value = on
        app?.let { AppSettings.putString(it, KEY_PARKED_ONLY, on.toString()) }
    }

    /** Whether [site]'s video is protected, and so most likely black in the car. */
    fun isProtected(site: CinemaSite): Boolean {
        val host = site.host
        return PROTECTED.any { host == it || host.endsWith(".$it") }
    }

    /** An address as typed: https:// in front when there's no scheme. */
    fun normalizeUrl(raw: String): String {
        val url = raw.trim()
        if (url.isEmpty()) return ""
        return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    }

    private fun save(list: List<CinemaSite>) {
        sitesFlow.value = list
        app?.let { AppSettings.putString(it, KEY_SITES, write(list)) }
    }

    private fun write(list: List<CinemaSite>): String = JSONArray(
        list.map { JSONObject().put("name", it.name).put("url", it.url).put("desktop", it.desktop) },
    ).toString()

    private fun read(json: String): List<CinemaSite> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").ifEmpty { return@mapNotNull null }
            CinemaSite(o.optString("name").ifEmpty { url }, url, o.optBoolean("desktop"))
        }
    }.getOrDefault(SUGGESTED)
}
