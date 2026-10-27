package io.github.sirionero.mycar.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/** Cover art arrives as a content:// or https:// URI; this fetches and remembers it. */
object MediaArt {

    /**
     * Weighed in bytes, not covers: one file's art can be 3000 pixels a side, 36 MB decoded, and
     * forty-eight of those ran a long drive out of memory.
     */
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun load(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        val key = uri.toString()
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val stream = when (uri.scheme) {
                    "http", "https" -> URL(key).openConnection().apply {
                        connectTimeout = 5_000
                        readTimeout = 8_000
                    }.getInputStream()
                    else -> context.contentResolver.openInputStream(uri)
                } ?: return@runCatching null
                decodeForCar(stream.use { it.readBytes() })
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    /** Decoded no bigger than the car shows it: a power-of-two step down to [MAX_SIDE_PX] at most. */
    private fun decodeForCar(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (side / sample > MAX_SIDE_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Larger than the cover ever shows on a car screen, beside the map or on its own. */
    private const val MAX_SIDE_PX = 1024
}
