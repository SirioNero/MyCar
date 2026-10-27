package io.github.sirionero.mycar.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.sirionero.mycar.MyCarLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Each Cinema site's own icon, as the site offers it to browsers and home screens - its web app
 * manifest, its apple-touch-icon, its icon links - fetched once and kept on the phone. Nothing of
 * the kind ships with myCar. Until one arrives, a tile shows the site's initial.
 */
object SiteIcons {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val iconsFlow = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val asked = mutableSetOf<String>()

    /** By host (see [CinemaSite.host]). */
    val icons: StateFlow<Map<String, ImageBitmap>> = iconsFlow.asStateFlow()

    /** The icons of [sites]: kept ones from the phone, the others fetched, in the background. */
    fun load(context: Context, sites: List<CinemaSite>) {
        val app = context.applicationContext
        for (site in sites) {
            val host = site.host
            if (host.isEmpty() || !synchronized(asked) { asked.add(host) }) continue
            scope.launch {
                val icon = fromDisk(app, host) ?: runCatching { fetch(site.url) }
                    .onFailure { MyCarLog.i("cinema: no icon for $host (${it.javaClass.simpleName})") }
                    .getOrNull()?.also { keep(app, host, it) }
                icon?.let { put(host, it) }
            }
        }
    }

    /** A page's own icon as the browser got it, kept while there's nothing better. */
    fun offer(context: Context, host: String, icon: Bitmap) {
        if (host.isEmpty() || host in iconsFlow.value || icon.width < MIN_PX) return
        val app = context.applicationContext
        val copy = icon.copy(Bitmap.Config.ARGB_8888, false) ?: return
        scope.launch {
            keep(app, host, copy)
            put(host, copy)
        }
    }

    private fun put(host: String, icon: Bitmap) {
        val image = icon.asImageBitmap()
        iconsFlow.value = iconsFlow.value + (host to image)
    }

    private fun file(context: Context, host: String) = File(File(context.filesDir, "cinema-icons"), "$host.png")

    private fun fromDisk(context: Context, host: String): Bitmap? =
        file(context, host).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }

    private fun keep(context: Context, host: String, icon: Bitmap) {
        val file = file(context, host)
        file.parentFile?.mkdirs()
        runCatching { file.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    /** The site's best icon: from its manifest, then its apple-touch-icon, then its icon links. */
    private fun fetch(url: String): Bitmap? {
        // The page's head only, where the links are: some sites (YouTube) put hundreds of kilobytes
        // of script in it before them.
        val page = get(url, MAX_PAGE_BYTES, until = HEAD_END)?.let { (finalUrl, body) -> finalUrl to String(body, Charsets.UTF_8) } ?: return null
        val (base, html) = page
        val links = LINK.findAll(html).map { attributes(it.value) }.toList()
        val candidates = buildList {
            links.firstOrNull { "manifest" in rel(it) }?.get("href")?.let { href ->
                addAll(manifestIcons(resolve(base, href) ?: return@let))
            }
            links.filter { rel(it).any { r -> r.startsWith("apple-touch-icon") } }
                .sortedByDescending { size(it["sizes"]) }
                .forEach { link -> link["href"]?.let { resolve(base, it) }?.let(::add) }
            links.filter { "icon" in rel(it) }
                .filterNot { it["type"]?.contains("svg") == true || it["href"]?.endsWith(".svg") == true }
                .sortedByDescending { size(it["sizes"]) }
                .forEach { link -> link["href"]?.let { resolve(base, it) }?.let(::add) }
            resolve(base, "/apple-touch-icon.png")?.let(::add)
            resolve(base, "/favicon.ico")?.let(::add)
        }
        val tries = candidates.distinct().take(MAX_TRIES)
        for (candidate in tries) {
            val bytes = get(candidate)?.second ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (bitmap.width >= MIN_PX) return scaled(bitmap)
        }
        MyCarLog.i("cinema: no usable icon at ${URI(base).host} (${links.size} links, ${tries.size} tried)")
        return null
    }

    /** The icons a web app manifest lists, biggest first, up to what a tile needs. */
    private fun manifestIcons(url: String): List<String> {
        val json = get(url)?.second?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() } ?: return emptyList()
        val icons = json.optJSONArray("icons") ?: return emptyList()
        return (0 until icons.length()).mapNotNull { icons.optJSONObject(it) }
            .filterNot { it.optString("type").contains("svg") || it.optString("src").endsWith(".svg") }
            // Up to 512 pixels, biggest first; bigger ones only after those.
            .sortedWith(compareBy({ size(it.optString("sizes")) > 512 }, { -size(it.optString("sizes")) }))
            .mapNotNull { resolve(url, it.optString("src")) }
    }

    /** Up to [max] bytes of [url], or up to the end of [until] when it comes sooner; null on an error. */
    private fun get(url: String, max: Long = MAX_BYTES, until: ByteString? = null): Pair<String, ByteArray>? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.source().use { source ->
                var end = -1L
                var asked = 0L
                while (end < 0 && asked < max) {
                    asked = minOf(max, asked + CHUNK)
                    val more = source.request(asked)
                    if (until != null) end = source.buffer.indexOf(until).let { if (it >= 0) it + until.size else -1 }
                    if (!more) break
                }
                source.buffer.readByteArray(if (end >= 0) end else minOf(source.buffer.size, max))
            }
            return response.request.url.toString() to body
        }
    }

    private fun scaled(bitmap: Bitmap): Bitmap =
        if (bitmap.width <= MAX_PX && bitmap.height <= MAX_PX) bitmap
        else Bitmap.createScaledBitmap(bitmap, MAX_PX, bitmap.height * MAX_PX / bitmap.width, true)

    private fun rel(link: Map<String, String>): List<String> = link["rel"].orEmpty().lowercase().split(' ').filter { it.isNotBlank() }

    /** The biggest of sizes such as "180x180" or "16x16 32x32"; 0 when unknown. */
    private fun size(sizes: String?): Int =
        sizes.orEmpty().split(' ').mapNotNull { it.substringBefore('x').toIntOrNull() }.maxOrNull() ?: 0

    private fun resolve(base: String, href: String): String? = runCatching {
        if (href.isBlank() || href.startsWith("data:")) null else URI(base).resolve(href.trim()).toString()
    }.getOrNull()?.takeIf { it.startsWith("http") }

    private fun attributes(tag: String): Map<String, String> =
        ATTRIBUTE.findAll(tag).associate { m -> m.groupValues[1].lowercase() to (m.groupValues[3].ifEmpty { m.groupValues[4] }.ifEmpty { m.groupValues[5] }) }

    private val LINK = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("([\\w-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")

    // As a phone's browser asks: some sites answer anything else with a page of their own.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    private const val MAX_BYTES = 512L * 1024
    private const val MAX_PAGE_BYTES = 2L * 1024 * 1024
    private const val CHUNK = 64L * 1024
    private val HEAD_END = "</head>".encodeUtf8()
    private const val MAX_TRIES = 6
    private const val MIN_PX = 48
    private const val MAX_PX = 256
}
