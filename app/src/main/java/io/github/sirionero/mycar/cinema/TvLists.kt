package io.github.sirionero.mycar.cinema

import android.content.Context
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** An IPTV list added on the phone: the name on its tile in the car, and the list's address. */
data class TvList(val name: String, val url: String) {
    val host: String
        get() = runCatching { URI(url).host }.getOrNull().orEmpty()
}

/** Where a list stands: its channels, as last downloaded and kept on the phone, and the last problem. */
data class TvListState(
    val channels: List<TvChannel> = emptyList(),
    val loading: Boolean = false,
    /** What went wrong the last time, in words for the screen; null when it went well. */
    val problem: String? = null,
    /** When the channels were downloaded; 0 when never. */
    val fetchedAt: Long = 0,
)

/**
 * Cinema's TV: IPTV lists (M3U, see [M3u]) added on the phone, each a tile in the car. A list is
 * downloaded when its tile opens, at most every few hours, and kept on the phone, so the channels
 * show at once and still do when the list's server doesn't answer.
 */
object TvLists {

    private const val KEY = "cinema_tv_lists"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var app: Context? = null
    private val listsFlow = MutableStateFlow<List<TvList>>(emptyList())
    private val statesFlow = MutableStateFlow<Map<String, TvListState>>(emptyMap())

    val lists: StateFlow<List<TvList>> = listsFlow.asStateFlow()

    /** By list address. */
    val states: StateFlow<Map<String, TvListState>> = statesFlow.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        listsFlow.value = read(AppSettings.getString(app, KEY, ""))
    }

    fun add(list: TvList) {
        save(listsFlow.value.filterNot { it.url == list.url } + list)
        app?.let { load(it, list, force = true) }
    }

    fun remove(list: TvList) {
        save(listsFlow.value.filterNot { it.url == list.url })
        statesFlow.update { it - list.url }
        app?.let { runCatching { file(it, list.url).delete() } }
    }

    /**
     * [list]'s channels: those kept on the phone at once, then a fresh download if they're older
     * than a few hours, or always with [force].
     */
    fun load(context: Context, list: TvList, force: Boolean = false) {
        val app = context.applicationContext
        val now = statesFlow.value[list.url]
        if (now?.loading == true) return
        if (!force && now != null && now.channels.isNotEmpty() && System.currentTimeMillis() - now.fetchedAt < FRESH_MS) return
        set(list.url) { it.copy(loading = true) }
        scope.launch {
            if (statesFlow.value[list.url]?.channels.isNullOrEmpty()) fromDisk(app, list)?.let { kept ->
                set(list.url) { it.copy(channels = kept.first, fetchedAt = kept.second) }
                if (!force && System.currentTimeMillis() - kept.second < FRESH_MS) {
                    set(list.url) { it.copy(loading = false, problem = null) }
                    return@launch
                }
            }
            val fetched = runCatching { download(app, list) }
            fetched.onSuccess { channels ->
                MyCarLog.i("cinema: TV list ${list.host}: ${channels.size} channels")
                set(list.url) { TvListState(channels, loading = false, problem = null, fetchedAt = System.currentTimeMillis()) }
            }.onFailure { error ->
                val problem = problemWith(app, list, error)
                MyCarLog.w("cinema: TV list ${list.host} didn't load - ${error.javaClass.simpleName}: ${error.message}")
                set(list.url) { it.copy(loading = false, problem = problem) }
            }
        }
    }

    private fun set(url: String, change: (TvListState) -> TvListState) =
        statesFlow.update { it + (url to change(it[url] ?: TvListState())) }

    private fun download(context: Context, list: TvList): List<TvChannel> {
        val request = Request.Builder().url(list.url).header("User-Agent", USER_AGENT).build()
        val (base, text) = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ListRefused(response.code)
            val source = response.body.source()
            source.request(MAX_BYTES + 1)
            if (source.buffer.size > MAX_BYTES) throw ListTooBig()
            response.request.url.toString() to source.buffer.readUtf8()
        }
        val channels = M3u.parse(text, base)
        if (channels.isEmpty()) throw NoChannels()
        val file = file(context, list.url)
        file.parentFile?.mkdirs()
        runCatching { file.writeText("$BASE_MARK$base\n$text") }
        return channels
    }

    /** The channels kept on the phone from the last download, and when that was. */
    private fun fromDisk(context: Context, list: TvList): Pair<List<TvChannel>, Long>? {
        val file = file(context, list.url).takeIf { it.isFile } ?: return null
        return runCatching {
            val text = file.readText()
            val base = text.lineSequence().firstOrNull()?.takeIf { it.startsWith(BASE_MARK) }?.removePrefix(BASE_MARK) ?: list.url
            M3u.parse(text, base) to file.lastModified()
        }.getOrNull()?.takeIf { it.first.isNotEmpty() }
    }

    private fun file(context: Context, url: String) =
        File(File(context.filesDir, "cinema-tv"), url.encodeUtf8().sha1().hex() + ".m3u")

    private fun problemWith(context: Context, list: TvList, error: Throwable): String = when {
        LocalNetwork.blocks(context, listOf(list.url)) -> LocalNetwork.HINT
        error is ListRefused -> Words.get(R.string.cinema_tv_refused, error.code)
        error is ListTooBig -> Words.get(R.string.cinema_tv_too_big, MAX_BYTES / 1024 / 1024)
        error is NoChannels -> Words.get(R.string.cinema_tv_nothing_playable)
        error is UnknownHostException -> Words.get(R.string.cinema_tv_unknown_host, list.host)
        error is SocketTimeoutException || error is ConnectException -> Words.get(R.string.cinema_tv_no_answer, list.host)
        else -> Words.get(R.string.cinema_tv_failed, error.message ?: error.javaClass.simpleName)
    }

    private fun save(list: List<TvList>) {
        listsFlow.value = list
        app?.let { AppSettings.putString(it, KEY, write(list)) }
    }

    private fun write(list: List<TvList>): String =
        JSONArray(list.map { JSONObject().put("name", it.name).put("url", it.url) }).toString()

    private fun read(json: String): List<TvList> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").ifEmpty { return@mapNotNull null }
            TvList(o.optString("name").ifEmpty { url }, url)
        }
    }.getOrDefault(emptyList())

    private class ListRefused(val code: Int) : IOException("HTTP $code")
    private class ListTooBig : IOException("too big")
    private class NoChannels : IOException("no channels")

    /** A fresh list is asked for again after this long. */
    private const val FRESH_MS = 6L * 60 * 60 * 1000
    /** The first line of a list kept on the phone: the address it came from, for relative ones. */
    private const val BASE_MARK = "#MYCAR-BASE:"
    private const val MAX_BYTES = 16L * 1024 * 1024
    private const val USER_AGENT = "myCar (Android)"
}
