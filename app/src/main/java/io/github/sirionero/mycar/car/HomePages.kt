package io.github.sirionero.mycar.car

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.MyCarLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** How a page of Home is cut up: the map's own page, two halves side by side, or four tiles two by two. */
internal enum class PageLayout(val id: String, val slots: Int) {
    MAP("map", 0),
    HALVES("halves", 2),
    GRID("grid", 4),
    ;

    companion object {
        fun of(id: String?): PageLayout? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One page of Home. [slots] says what each part shows, left to right and then top to bottom: a
 * widget ([PageContent]), the browser, or a car app by its component id; null for nothing yet.
 */
internal data class HomePage(val id: Int, val layout: PageLayout, val slots: List<String?> = List(layout.slots) { null })

/** What a part of a page can show besides a car app, which goes by its component id. */
internal object PageContent {
    const val WEATHER = "widget:weather"
    const val AGENDA = "widget:agenda"

    /** myCar's own browser, by the id the launcher and the dock know it by. */
    const val BROWSER = BUILT_IN_PREFIX + "web"
}

/**
 * Home's pages: the map's own - the map with the music or an app beside it - and the driver's
 * pages either side of it, swiped to. Until the driver changes any, Home has the ready-made ones
 * ([readyMade]), worked out afresh from the apps installed, so Plancia installed later still
 * gets its page; the first change saves the lot.
 */
object HomePages {
    private const val KEY = "car_home_pages"
    private var app: Context? = null
    private val flow = MutableStateFlow<List<HomePage>?>(null)

    /** The driver's pages, or null while they're still the ready-made ones. */
    internal val saved: StateFlow<List<HomePage>?> = flow

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        flow.value = AppSettings.getString(app, KEY, "").takeIf { it.isNotEmpty() }?.let(::decode)
    }

    internal fun save(pages: List<HomePage>) {
        flow.value = pages
        app?.let { AppSettings.putString(it, KEY, encode(pages)) }
    }

    /** Back to the ready-made pages. */
    fun reset() {
        flow.value = null
        app?.let { AppSettings.putString(it, KEY, "") }
    }

    private fun encode(pages: List<HomePage>): String = JSONArray().apply {
        pages.forEach { page ->
            put(
                JSONObject()
                    .put("id", page.id)
                    .put("layout", page.layout.id)
                    .put("slots", JSONArray().apply { page.slots.forEach { put(it ?: JSONObject.NULL) } }),
            )
        }
    }.toString()

    private fun decode(json: String): List<HomePage>? = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val page = array.getJSONObject(i)
            val layout = PageLayout.of(page.optString("layout")) ?: return@mapNotNull null
            val slots = page.optJSONArray("slots")
            HomePage(
                page.getInt("id"),
                layout,
                List(layout.slots) { s -> slots?.takeIf { s < it.length() && !it.isNull(s) }?.getString(s) },
            )
        }.let(::repaired)
    }.onFailure { MyCarLog.w("home pages: unreadable, back to the ready-made ones (${it.javaClass.simpleName})") }.getOrNull()
}

/** Plancia, the Home Assistant car app: the ready-made pages give it a half of its own. */
internal const val PLANCIA_PACKAGE = "it.plancia.ha"

/**
 * The pages Home starts with: weather and agenda on the left of the map, and on its right
 * Plancia beside the browser, when [plancia] (Plancia's component id) is installed.
 */
internal fun readyMade(plancia: String?): List<HomePage> = buildList {
    add(HomePage(1, PageLayout.HALVES, listOf(PageContent.WEATHER, PageContent.AGENDA)))
    add(HomePage(0, PageLayout.MAP))
    if (plancia != null) add(HomePage(2, PageLayout.HALVES, listOf(plancia, PageContent.BROWSER)))
}

/** Exactly one map page, never a duplicate id, and as many slots as each layout has. */
internal fun repaired(pages: List<HomePage>): List<HomePage> {
    val seen = mutableSetOf<Int>()
    var map = false
    val kept = pages.mapNotNull { page ->
        if (!seen.add(page.id)) return@mapNotNull null
        if (page.layout == PageLayout.MAP) {
            if (map) return@mapNotNull null
            map = true
        }
        page.copy(slots = List(page.layout.slots) { page.slots.getOrNull(it) })
    }
    if (map) return kept
    val id = (kept.maxOfOrNull { it.id } ?: -1) + 1
    return listOf(HomePage(id, PageLayout.MAP)) + kept
}

/** [content] in part [slot] of page [pageId]; any other part of that page showing it is emptied. */
internal fun List<HomePage>.withSlot(pageId: Int, slot: Int, content: String?): List<HomePage> = map { page ->
    if (page.id != pageId) page
    else page.copy(slots = page.slots.mapIndexed { i, old -> if (i == slot) content else old?.takeIf { it != content } })
}

/** Page [pageId] cut up as [layout]: what it showed stays, in order, as far as there's room. */
internal fun List<HomePage>.withLayout(pageId: Int, layout: PageLayout): List<HomePage> = map { page ->
    if (page.id != pageId || page.layout == PageLayout.MAP || layout == PageLayout.MAP) page
    else {
        val shown = page.slots.filterNotNull()
        page.copy(layout = layout, slots = List(layout.slots) { shown.getOrNull(it) })
    }
}

/** A new page of two empty halves just after page [afterId], and its id. */
internal fun List<HomePage>.withNewPage(afterId: Int): Pair<List<HomePage>, Int> {
    val id = (maxOfOrNull { it.id } ?: -1) + 1
    val at = indexOfFirst { it.id == afterId }.let { if (it < 0) size else it + 1 }
    return toMutableList().apply { add(at, HomePage(id, PageLayout.HALVES)) } to id
}

/** Without page [pageId]; the map's page always stays. */
internal fun List<HomePage>.without(pageId: Int): List<HomePage> =
    filterNot { it.id == pageId && it.layout != PageLayout.MAP }

/** Page [pageId] swapped with its neighbour on the left ([by] -1) or the right (+1), the map's page included. */
internal fun List<HomePage>.moved(pageId: Int, by: Int): List<HomePage> {
    val from = indexOfFirst { it.id == pageId }
    val to = from + by
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Plancia's component id, when it's installed as a car app. */
internal fun planciaIn(entries: List<Entry>): String? =
    entries.firstOrNull { it.template?.component?.packageName == PLANCIA_PACKAGE && it.fitsBesideMap }?.componentId

/** The pages Home shows: the driver's own, or the ready-made ones for the apps installed now. */
@Composable
internal fun rememberHomePages(): List<HomePage> {
    val saved by HomePages.saved.collectAsState()
    val entries = rememberAllEntries()
    val plancia = remember(entries) { planciaIn(entries) }
    return remember(saved, plancia) { saved ?: readyMade(plancia) }
}
