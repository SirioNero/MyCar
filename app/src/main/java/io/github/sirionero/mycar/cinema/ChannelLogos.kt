package io.github.sirionero.mycar.cinema

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * TV channels' logos, from the addresses their list gives (tvg-logo): a few at a time, made small
 * for a tile and kept while there's room. One that doesn't load isn't asked for again this drive;
 * its channel shows its initial.
 */
object ChannelLogos {

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val gate = Semaphore(PARALLEL)
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val logos = object : LruCache<String, ImageBitmap>(KEPT_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Logos that didn't come (a tunnel, a slow server) are tried again: when a list opens. */
    fun forgetFailures() = failed.clear()

    /** The logo at [url], if it's kept already: no waiting. */
    fun kept(url: String): ImageBitmap? = logos.get(url)

    suspend fun load(url: String): ImageBitmap? {
        logos.get(url)?.let { return it }
        if (url in failed) return null
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                logos.get(url) ?: runCatching { fetch(url) }.getOrNull()?.also { logos.put(url, it) }
            }
        }.also { if (it == null) failed.add(url) }
    }

    private fun fetch(url: String): ImageBitmap? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val bytes = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val source = response.body.source()
            source.request(MAX_BYTES + 1)
            if (source.buffer.size > MAX_BYTES) return null
            source.buffer.readByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    private const val PARALLEL = 4
    private const val MAX_PX = 192
    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val KEPT_BYTES = 12 * 1024 * 1024
    private const val USER_AGENT = "myCar (Android)"
}
