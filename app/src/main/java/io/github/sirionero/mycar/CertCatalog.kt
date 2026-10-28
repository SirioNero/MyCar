package io.github.sirionero.mycar

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * The public car certificates myCar can download, the recommended one first. Nothing is shipped:
 * only where each one is and the SHA-256 it must have ([CertChecks]), so a changed or swapped file
 * is left out and the next copy tried. Each is kept in a file of its own, and the one in use is
 * copied into the downloaded slot that [CertProvider] reads.
 */
object CertCatalog {
    private val log = MyCarLog.tagged("CERT")

    class Entry(
        val id: String,
        /** As the phone shows it: the project's name. */
        val label: String,
        /** Where it comes from, and that project's licence. */
        val project: String,
        /** The same file in several places, tried in order: a fixed commit, then branches. */
        val certUrls: List<String>,
        /** The key beside each certificate, in the same order. */
        val keyUrls: List<String>,
        /** SHA-256 of the certificate (DER), in capitals without separators. */
        val sha256: String,
    )

    private const val AASDK = "https://raw.githubusercontent.com/opencardev/aasdk"
    private const val AASDK_COMMIT = "5483c6481742d35bd9ed5923eb74a3562eeab84b"
    private const val REVIVED = "https://raw.githubusercontent.com/andreknieriem/headunit-revived"
    private const val REVIVED_COMMIT = "620af558d7e7e98c9b64053177b636d3a692d233"

    /** Taken by most cars tried so far. */
    val aasdk = Entry(
        id = "aasdk",
        label = "aasdk",
        project = "opencardev/aasdk, GPL-3.0",
        certUrls = listOf(AASDK_COMMIT, "main", "newdev").map { "$AASDK/$it/cert/headunit.crt" },
        keyUrls = listOf(AASDK_COMMIT, "main", "newdev").map { "$AASDK/$it/cert/headunit.key" },
        sha256 = "1C0E0EF9E672DD1A63AB4D61AFAA8996A57CA7AA966D1922B97D8F9385EA3C35",
    )

    /** The spare, valid until 2044, for a car that turns down the first. */
    val revived = Entry(
        id = "revived",
        label = "Headunit Revived",
        project = "andreknieriem/headunit-revived, AGPL-3.0",
        certUrls = listOf(REVIVED_COMMIT, "main").map { "$REVIVED/$it/app/src/main/res/raw/cert" },
        keyUrls = listOf(REVIVED_COMMIT, "main").map { "$REVIVED/$it/app/src/main/res/raw/privkey" },
        sha256 = "E323CC6DD52D1F2A280360A0E92EFA04AB3664DD68FAB3DD187F03D663FEED6D",
    )

    /** In the order myCar offers and tries them: the recommended one first. */
    val entries = listOf(aasdk, revived)

    fun entry(id: String): Entry? = entries.firstOrNull { it.id == id }

    /** Which catalog certificate [cert] is, by its fingerprint; null for one from elsewhere. */
    fun idOf(cert: X509Certificate, list: List<Entry> = entries): String? {
        val sha = CertChecks.sha256(cert)
        return list.firstOrNull { it.sha256 == sha }?.id
    }

    // --- download --------------------------------------------------------------------------

    class Downloads(val ready: List<String>, val failed: Map<String, String>)

    /**
     * Gets every certificate of the catalog not stored yet, so a spare is there if the car turns
     * down the first. Synchronous: call it off the main thread.
     */
    fun downloadAll(context: Context): Downloads = downloadAll(context.filesDir, entries, CertDownloader::fetchText)

    internal fun downloadAll(base: File, list: List<Entry>, fetch: (String) -> String): Downloads {
        val ready = mutableListOf<String>()
        val failed = mutableMapOf<String, String>()
        for (entry in list) {
            if (stored(base, entry.id)) {
                ready += entry.id
                continue
            }
            runCatching { store(base, entry.id, fetchVerified(entry, fetch)) }
                .onSuccess {
                    ready += entry.id
                    log.i("${entry.label}: downloaded and checked")
                }
                .onFailure { failed[entry.id] = it.message ?: it.javaClass.simpleName }
        }
        return Downloads(ready, failed)
    }

    /** The certificate of [entry] from the first place that has the right one; throws when none has. */
    internal fun fetchVerified(entry: Entry, fetch: (String) -> String): PemKeyParser.Result {
        var problem: Exception? = null
        for ((certUrl, keyUrl) in entry.certUrls.zip(entry.keyUrls)) {
            try {
                val certificatePem = fetch(certUrl)
                // The certificate first: a wrong one isn't worth fetching its key for.
                CertChecks.checkFingerprint(entry, certificatePem)
                return CertChecks.verify(entry, certificatePem, fetch(keyUrl))
            } catch (e: Exception) {
                problem = e
                log.w("${entry.label}: $certUrl - ${e.message}")
            }
        }
        throw IllegalStateException("couldn't get ${entry.label}: ${problem?.message}", problem)
    }

    // --- storage ---------------------------------------------------------------------------

    private const val DIR = "identity-catalog"
    private const val ALIAS = "phone"
    /** The downloaded slot's password, so a copy works there as it is. */
    private const val PASSWORD = "aaspike"

    internal fun file(base: File, id: String) = File(File(base, DIR), "$id.p12")

    internal fun stored(base: File, id: String) = file(base, id).isFile

    /** The catalog certificates on the phone, in catalog order. */
    fun present(context: Context): List<String> = entries.filter { stored(context.filesDir, it.id) }.map { it.id }

    /** Forgets every downloaded catalog certificate (Settings > Remove downloaded certificate). */
    fun removeAll(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }

    internal fun store(base: File, id: String, result: PemKeyParser.Result) {
        val password = PASSWORD.toCharArray()
        // The key under the file's own password: kept under an empty one, as PemKeyParser leaves
        // it, only some PKCS#12 implementations open it again with the file's.
        val key = result.keyStore.getKey(ALIAS, CharArray(0))
        val p12 = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(ALIAS, key, password, arrayOf(result.certificate))
        }
        val bytes = ByteArrayOutputStream().use { out ->
            p12.store(out, password)
            out.toByteArray()
        }
        writeAtomically(file(base, id), bytes)
    }

    /** Puts [id] in use: it becomes the downloaded certificate that [CertProvider] reads. */
    fun activate(context: Context, id: String) {
        activate(context.filesDir, id)
        CertProvider.warm(context)
    }

    internal fun activate(base: File, id: String) {
        val source = file(base, id)
        require(source.isFile) { "$id isn't downloaded" }
        // Written aside and renamed: a connection reading the slot never finds half a file.
        writeAtomically(CertProvider.downloadedSlot(base), source.readBytes())
        log.i("$id is the certificate in use")
    }

    /**
     * A certificate downloaded before the catalog (aasdk, straight into the slot) is kept in the
     * catalog too, so going to the spare and back doesn't need it downloaded again.
     */
    fun adoptLegacy(context: Context) = adoptLegacy(context.filesDir)

    internal fun adoptLegacy(base: File, list: List<Entry> = entries) {
        val slot = CertProvider.downloadedSlot(base)
        if (!slot.isFile) return
        val cert = runCatching { readCertificate(slot) }.getOrNull() ?: return
        val id = idOf(cert, list) ?: return
        if (stored(base, id)) return
        writeAtomically(file(base, id), slot.readBytes())
        log.i("kept the $id certificate downloaded earlier")
    }

    internal fun readCertificate(p12: File): X509Certificate {
        val keyStore = KeyStore.getInstance("PKCS12")
        p12.inputStream().use { keyStore.load(it, PASSWORD.toCharArray()) }
        val alias = keyStore.aliases().asSequence().first { keyStore.isKeyEntry(it) }
        return keyStore.getCertificate(alias) as X509Certificate
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.new")
        temp.writeBytes(bytes)
        check(temp.renameTo(target)) { "couldn't store ${target.name}" }
    }
}
