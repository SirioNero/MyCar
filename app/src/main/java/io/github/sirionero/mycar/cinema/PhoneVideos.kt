package io.github.sirionero.mycar.cinema

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.sirionero.mycar.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A video on the phone, as Android's media store knows it. */
data class PhoneVideo(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val folder: String,
)

/**
 * Cinema's videos on the phone: those Android's media store lists (the camera's, downloads,
 * films copied over), newest first. Reading them needs Android's "videos" permission, asked on
 * the phone; with "allow limited access" (Android 14) the ones the driver picked show.
 */
object PhoneVideos {

    /** What to ask Android for: on Android 14 and later, the limited access too. */
    val permissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** Whether myCar may see the phone's videos, all of them or those picked. */
    fun allowed(context: Context): Boolean =
        permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** Only the videos picked for myCar (Android 14's limited access), not all of them. */
    fun limited(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    /** The phone's videos, newest first; none without the permission. */
    suspend fun list(context: Context): List<PhoneVideo> = withContext(Dispatchers.IO) {
        if (!allowed(context)) return@withContext emptyList()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val columns = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val query = Bundle().apply {
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Video.Media.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_VIDEOS)
        }
        runCatching {
            context.contentResolver.query(collection, columns, query, null)?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val width = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val height = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val folder = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        val videoId = cursor.getLong(id)
                        val fileName = cursor.getString(name).orEmpty()
                        add(
                            PhoneVideo(
                                id = videoId,
                                uri = ContentUris.withAppendedId(collection, videoId),
                                title = cursor.getString(title)?.ifBlank { null } ?: fileName.substringBeforeLast('.'),
                                durationMs = cursor.getLong(duration),
                                width = cursor.getInt(width),
                                height = cursor.getInt(height),
                                folder = cursor.getString(folder).orEmpty(),
                            ),
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    private val thumbnails = object : LruCache<Long, ImageBitmap>(THUMBNAIL_BYTES) {
        override fun sizeOf(key: Long, value: ImageBitmap) = value.width * value.height * 4
    }

    /** [video]'s picture for its tile, made by Android and kept while there's room. */
    suspend fun thumbnail(context: Context, video: PhoneVideo): ImageBitmap? {
        thumbnails.get(video.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(video.uri, Size(THUMB_WIDTH, THUMB_WIDTH * 9 / 16), null) }
                .getOrNull()?.asImageBitmap()?.also { thumbnails.put(video.id, it) }
        }
    }

    // Where each video was left, so it starts again from there: the last few, across drives.

    private const val KEY_RESUME = "cinema_resume"

    /** Where [key] was left, if far enough in and not at its very end; 0 to start from the top. */
    fun resumeAt(context: Context, key: String, durationMs: Long): Long {
        val at = runCatching { JSONObject(AppSettings.getString(context, KEY_RESUME, "{}")).optLong(key) }.getOrDefault(0L)
        return if (at < RESUME_MIN_MS || (durationMs > 0 && at > durationMs - RESUME_END_MS)) 0L else at
    }

    fun leftAt(context: Context, key: String, positionMs: Long) {
        val json = runCatching { JSONObject(AppSettings.getString(context, KEY_RESUME, "{}")) }.getOrDefault(JSONObject())
        json.remove(key)
        // JSONObject keeps the order things went in: the oldest go first when there are too many.
        val keys = json.keys().asSequence().toList()
        keys.take((keys.size - RESUME_KEPT + 1).coerceAtLeast(0)).forEach(json::remove)
        if (positionMs >= RESUME_MIN_MS) json.put(key, positionMs)
        AppSettings.putString(context, KEY_RESUME, json.toString())
    }

    private const val MAX_VIDEOS = 1000
    private const val THUMB_WIDTH = 320
    private const val THUMBNAIL_BYTES = 16 * 1024 * 1024
    private const val RESUME_MIN_MS = 30_000L
    private const val RESUME_END_MS = 60_000L
    private const val RESUME_KEPT = 30
}
