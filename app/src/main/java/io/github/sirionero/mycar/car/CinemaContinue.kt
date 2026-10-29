package io.github.sirionero.mycar.car

import android.content.Context
import android.net.Uri
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.cinema.CinemaSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * What Cinema showed last - a site at the page it was on, or a video or channel - kept on the
 * phone. Cinema isn't kept behind other screens, so a look at the map lost it: "Continue" opens
 * it again, a video where it was left.
 */
internal object CinemaContinue {

    sealed interface Last {
        val title: String
    }

    /** A site, by its address on the list ([url]), at the [page] it was showing. */
    data class Site(val url: String, override val title: String, val page: String?) : Last

    data class Media(val media: CinemaMedia) : Last {
        override val title: String get() = media.title
    }

    private const val KEY = "cinema_continue"
    private val flow = MutableStateFlow<Last?>(null)
    val last: StateFlow<Last?> = flow.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        flow.value = read(AppSettings.getString(context, KEY, ""))
    }

    fun site(context: Context, site: CinemaSite, page: String? = null) = save(context, Site(site.url, site.name, page))

    /** Nothing to continue: the car preview starts every shot from Cinema's own tiles. */
    fun reset(context: Context) {
        loaded = true
        flow.value = null
        AppSettings.putString(context, KEY, "")
    }

    fun media(context: Context, media: CinemaMedia) = save(context, Media(media))

    private fun save(context: Context, last: Last) {
        flow.value = last
        AppSettings.putString(context, KEY, write(last))
    }

    private fun write(last: Last): String = when (last) {
        is Site -> JSONObject().put("kind", "site").put("url", last.url).put("title", last.title).put("page", last.page.orEmpty())
        is Media -> JSONObject().put("kind", "media").put("title", last.media.title).put("uri", last.media.uri.toString())
            .put("headers", JSONObject(last.media.headers)).put("live", last.media.live).put("resume", last.media.resumeKey.orEmpty())
    }.toString()

    private fun read(json: String): Last? = runCatching {
        if (json.isEmpty()) return null
        val o = JSONObject(json)
        when (o.optString("kind")) {
            "site" -> Site(o.getString("url"), o.optString("title"), o.optString("page").ifEmpty { null })
            "media" -> {
                val h = o.optJSONObject("headers")
                val headers = h?.keys()?.asSequence()?.associateWith { h.optString(it) }.orEmpty()
                Media(CinemaMedia(o.optString("title"), Uri.parse(o.getString("uri")), headers, o.optBoolean("live"), o.optString("resume").ifEmpty { null }))
            }
            else -> null
        }
    }.getOrNull()
}
