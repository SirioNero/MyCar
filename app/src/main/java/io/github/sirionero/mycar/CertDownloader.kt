package io.github.sirionero.mycar

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a public head unit certificate or key file from GitHub ([CertCatalog] says which, and
 * from where). The app ships no certificates; it downloads them only when the user chooses to,
 * the same way a browser would.
 */
object CertDownloader {

    private val log = MyCarLog.tagged("DOWNL")

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    /** Far more than a certificate or a key needs: a page served in their place stops here. */
    private const val MAX_BYTES = 64 * 1024

    /** The file at [url] as text; throws with a readable message on any failure. */
    fun fetchText(url: String): String {
        val name = url.substringAfterLast('/')
        log.i("fetching $url")
        val body = try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw RuntimeException("it's far too big for a certificate")
                }
                out.toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            throw RuntimeException("could not download $name: ${e.message}", e)
        }
        if (body.isBlank()) throw RuntimeException("$name is empty")
        log.i("downloaded $name (${body.length} bytes)")
        return body
    }
}
