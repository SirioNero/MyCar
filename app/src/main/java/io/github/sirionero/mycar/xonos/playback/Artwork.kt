package io.github.sirionero.mycar.xonos.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.xonos.Xonos
import io.github.sirionero.mycar.xonos.data.MaImage
import io.github.sirionero.mycar.xonos.data.MaRepository
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Le copertine per l'auto. Gearslip non apre indirizzi http (e quello di Music Assistant di solito
 * lo è), quindi gli si danno indirizzi content:// di questa app: [ArtworkProvider] scarica
 * l'immagine dal server, la tiene da parte e la passa. Gli indirizzi sono firmati, così nessun'altra
 * app può usare il provider per farsi scaricare altro.
 */
class Artwork(private val context: Context) {

    private val key: ByteArray by lazy {
        Xonos.init(context)
        Xonos.settings.artKey
    }

    /** L'indirizzo content:// di un'immagine di Music Assistant, alla misura richiesta. */
    fun uriFor(image: MaImage?, size: Int): Uri? {
        image ?: return null
        val value = when {
            image.proxyId != null -> "id:${image.proxyId}"
            image.remote && image.path.startsWith("https://") -> "url:${image.path}"
            else -> "path:${image.provider}|${image.path}"
        }
        val encoded = encode(value)
        return Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath("img")
            .appendQueryParameter("v", encoded)
            .appendQueryParameter("s", size.toString())
            .appendQueryParameter("h", sign(key, "$encoded|$size"))
            .build()
    }

    /** L'icona di una voce del menu (vedi [ICONS]). */
    fun iconUri(name: String): Uri = Uri.Builder()
        .scheme("content")
        .authority(authority(context))
        .appendPath("icon")
        .appendPath(name)
        .build()

    companion object {
        /** Le icone del menu, disegnate dall'app. */
        val ICONS = mapOf(
            "recent" to R.drawable.xonos_cat_recent,
            "foryou" to R.drawable.xonos_cat_foryou,
            "playlist" to R.drawable.xonos_cat_playlist,
            "album" to R.drawable.xonos_cat_album,
            "artist" to R.drawable.xonos_cat_artist,
            "favorite" to R.drawable.xonos_cat_favorite,
            "radio" to R.drawable.xonos_cat_radio,
            "podcast" to R.drawable.xonos_cat_podcast,
            "audiobook" to R.drawable.xonos_cat_audiobook,
            "browse" to R.drawable.xonos_cat_browse,
            "track" to R.drawable.xonos_cat_track,
            "genre" to R.drawable.xonos_cat_genre,
        )

        fun authority(context: Context) = "${context.packageName}.xonos.art"

        private fun encode(value: String) =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

        private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

        private fun sign(key: ByteArray, text: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            val digest = mac.doFinal(text.toByteArray(Charsets.UTF_8))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(16))
        }

        private fun hash(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        /** Il file con l'immagine chiesta, scaricato o disegnato se non c'è ancora. Gira su un thread del binder. */
        fun open(context: Context, uri: Uri): File? {
            Xonos.init(context)
            return when (uri.pathSegments.firstOrNull()) {
                "icon" -> icon(context, File(context.cacheDir, "xonos-art").apply { mkdirs() }, uri.lastPathSegment ?: return null)
                "img" -> image(Xonos, uri)
                else -> null
            }
        }

        private fun image(app: Xonos, uri: Uri): File? {
            val encoded = uri.getQueryParameter("v") ?: return null
            val size = uri.getQueryParameter("s")?.toIntOrNull() ?: return null
            val signature = uri.getQueryParameter("h") ?: return null
            if (signature != sign(app.settings.artKey, "$encoded|$size")) return null
            return app.artCache.get(hash("$encoded|$size") + ".img") {
                val value = decode(encoded)
                val base = app.repo.baseUrl ?: app.settings.load().urls.firstOrNull()
                when {
                    value.startsWith("url:") -> value.removePrefix("url:")
                    base == null -> null
                    value.startsWith("id:") -> MaRepository.imageUrl(base, MaImage("", "", value.removePrefix("id:"), false), size)
                    value.startsWith("path:") -> {
                        val (provider, path) = value.removePrefix("path:").split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                        MaRepository.imageUrl(base, MaImage(path, provider, null, false), size)
                    }
                    else -> null
                }
            }
        }

        /** Un'icona del menu: il disegno dell'app su un quadrato arrotondato, in PNG. */
        private fun icon(context: Context, dir: File, name: String): File? {
            val res = ICONS[name] ?: return null
            val file = File(dir, "icon-$name.png")
            if (file.length() > 0) return file
            val drawable = ContextCompat.getDrawable(context, res) ?: return null
            val size = 256
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.xonos_panel) }
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.18f, size * 0.18f, paint)
            val inset = (size * 0.22f).toInt()
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.setTint(ContextCompat.getColor(context, R.color.xonos_warm))
            drawable.draw(canvas)
            // Un file temporaneo tutto suo, come per le copertine (vedi ArtCache).
            val temp = File.createTempFile(file.name, ".part", dir)
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            if (!temp.renameTo(file)) temp.delete()
            return file.takeIf { it.length() > 0 }
        }
    }
}

/** Passa le copertine a Gearslip e alla notifica (vedi [Artwork]). */
class ArtworkProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Solo lettura")
        val context = context ?: throw FileNotFoundException()
        val file = Artwork.open(context, uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String =
        if (uri.pathSegments.firstOrNull() == "icon") "image/png" else "image/jpeg"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
