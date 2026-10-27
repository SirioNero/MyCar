package io.github.sirionero.mycar.home

import org.json.JSONObject
import java.text.Collator
import java.util.Locale

/** A Home Assistant dashboard, as its sidebar lists it. [urlPath] has no leading "/". */
data class Dashboard(val urlPath: String, val title: String)

/**
 * The dashboards for Plancia's menu, in the order of Home Assistant's own sidebar: the ones the
 * driver reordered first, then the default one, the other dashboards alphabetically, Energy, Map,
 * Logbook and History, then the rest. The rules are its frontend's.
 */
object Panels {

    /** Panels that aren't dashboards: settings, the profile and service pages. */
    private val EXCLUDED = setOf("profile", "config", "notfound", "_my_redirect", "app", "developer-tools")

    private val BUILT_IN_ORDER = mapOf("energy" to 1, "map" to 2, "logbook" to 3, "history" to 4)

    /** Built-in panels have a translation key of the frontend for a title. */
    private val TITLES = mapOf(
        "home" to "Overview",
        "states" to "Overview",
        "energy" to "Energy",
        "map" to "Map",
        "logbook" to "Logbook",
        "history" to "History",
        "calendar" to "Calendar",
        "todo" to "To-do lists",
        "shopping_list" to "Shopping list",
        "media_browser" to "Media",
        "light" to "Lights",
        "security" to "Security",
        "climate" to "Climate",
        "maintenance" to "Maintenance",
        "apps" to "Apps",
        "hassio" to "Supervisor",
    )

    /** The dashboard Home Assistant opens by itself. */
    fun defaultPanel(panels: JSONObject, userDefault: String?, systemDefault: String?): String {
        val hasHome = panels.has("home")
        val chosen = userDefault ?: systemDefault ?: if (hasHome) "home" else "lovelace"
        // Where the old Overview is gone, "lovelace" leads to the new one.
        val legacyOverview = panels.optJSONObject("lovelace")?.optJSONObject("config") != null
        return if (chosen == "lovelace" && hasHome && !legacyOverview) "home" else chosen
    }

    fun dashboards(panels: JSONObject, order: List<String>, hidden: Set<String>, defaultPanel: String): List<Dashboard> {
        val entries = mutableListOf<Entry>()
        val keys = panels.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val panel = panels.optJSONObject(key) ?: continue
            val urlPath = panel.str("url_path") ?: key
            if (urlPath in EXCLUDED) continue
            val rawTitle = panel.str("title")
            val shown = rawTitle != null &&
                panel.optBoolean("show_in_sidebar", true) &&
                urlPath !in hidden &&
                (panel.optBoolean("default_visible", true) || urlPath in order)
            if (urlPath != defaultPanel && !shown) continue
            entries += Entry(urlPath, title(urlPath, rawTitle), panel.str("component_name") == "lovelace")
        }

        val collator = Collator.getInstance(Locale.getDefault())
        val reverse = order.reversed()
        return entries.sortedWith { a, b ->
            val ia = reverse.indexOf(a.urlPath)
            val ib = reverse.indexOf(b.urlPath)
            val sa = BUILT_IN_ORDER[a.urlPath]
            val sb = BUILT_IN_ORDER[b.urlPath]
            when {
                ia != ib -> if (ia < ib) 1 else -1
                a.urlPath == defaultPanel -> -1
                b.urlPath == defaultPanel -> 1
                a.lovelace && b.lovelace -> collator.compare(a.title, b.title)
                a.lovelace -> -1
                b.lovelace -> 1
                sa != null && sb != null -> sa - sb
                sa != null -> -1
                sb != null -> 1
                else -> collator.compare(a.title, b.title)
            }
        }.map { Dashboard(it.urlPath, it.title) }
    }

    fun title(urlPath: String, rawTitle: String?): String = when {
        rawTitle != null -> TITLES[rawTitle] ?: rawTitle
        urlPath == "lovelace" -> "Overview"
        else -> urlPath.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private class Entry(val urlPath: String, val title: String, val lovelace: Boolean)
}
