package io.github.sirionero.mycar

import android.content.Context
import androidx.annotation.StringRes
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
        /** SHA-256 of the certificate (DER), in capitals without separators; null for one of a [family]. */
        val sha256: String?,
        /** For a certificate its source replaces every few months: what each new one must be. */
        val family: Family? = null,
        /** Its place in the phone's list ("Recommended", "Spare"...). */
        @StringRes private val roleText: Int = 0,
        /** What it's for, under its name in Settings. */
        @StringRes private val aboutText: Int = 0,
    ) {
        /** [roleText] in myCar's language; empty for none. */
        val role: String get() = if (roleText == 0) "" else Words.get(roleText)

        /** [aboutText] in myCar's language; empty for none. */
        val about: String get() = if (aboutText == 0) "" else Words.get(aboutText)
    }

    /**
     * A certificate its source replaces every few months, as Android Auto does its own: no fixed
     * SHA-256, but always one naming [organisation] and signed by the same root ([rootSha256],
     * named [rootOrganisation]), which the file carries after it.
     */
    class Family(val organisation: String, val rootOrganisation: String, val rootSha256: String)

    private const val AASDK = "https://raw.githubusercontent.com/opencardev/aasdk"
    private const val AASDK_COMMIT = "5483c6481742d35bd9ed5923eb74a3562eeab84b"
    private const val REVIVED = "https://raw.githubusercontent.com/andreknieriem/headunit-revived"
    private const val REVIVED_COMMIT = "620af558d7e7e98c9b64053177b636d3a692d233"
    private const val ARCHIVE = "https://raw.githubusercontent.com/Iscle/android-auto-certificates"
    private const val ARCHIVE_COMMIT = "c20cc85eb7ff8dee1f8947e9b310add420901557"

    /** Google Automotive Link, the root every car trusts (the other two are signed by it too), valid until 2044. */
    private const val GOOGLE_AUTOMOTIVE_LINK = "49E52EFC13AD2ED09F204C3B10698BD84BB7105F510558AA14B8119A5C4AD17F"

    /** Taken by most cars tried so far. */
    val aasdk = Entry(
        id = "aasdk",
        label = "aasdk",
        project = "opencardev/aasdk, GPL-3.0",
        certUrls = listOf(AASDK_COMMIT, "main", "newdev").map { "$AASDK/$it/cert/headunit.crt" },
        keyUrls = listOf(AASDK_COMMIT, "main", "newdev").map { "$AASDK/$it/cert/headunit.key" },
        sha256 = "1C0E0EF9E672DD1A63AB4D61AFAA8996A57CA7AA966D1922B97D8F9385EA3C35",
        roleText = R.string.cert_role_recommended,
        aboutText = R.string.cert_about_aasdk,
    )

    /** The spare, valid until 2044, for a car that turns down the first. */
    val revived = Entry(
        id = "revived",
        label = "Headunit Revived",
        project = "andreknieriem/headunit-revived, AGPL-3.0",
        certUrls = listOf(REVIVED_COMMIT, "main").map { "$REVIVED/$it/app/src/main/res/raw/cert" },
        keyUrls = listOf(REVIVED_COMMIT, "main").map { "$REVIVED/$it/app/src/main/res/raw/privkey" },
        sha256 = "E323CC6DD52D1F2A280360A0E92EFA04AB3664DD68FAB3DD187F03D663FEED6D",
        roleText = R.string.cert_role_spare,
        aboutText = R.string.cert_about_revived,
    )

    /**
     * Android Auto's own (CarService), for the cars that check phones most strictly, recent
     * Volkswagens among them. Android Auto replaces it every few months, and the archive keeps the
     * newest under latest: so no fixed fingerprint, but Google's root ([Family]), and fetched anew
     * before it runs out ([refreshIfDue]). The branch first, for the newest; the commit last.
     */
    val carService = Entry(
        id = "carservice",
        label = "CarService",
        project = "Iscle/android-auto-certificates, taken from Android Auto",
        certUrls = listOf("main", ARCHIVE_COMMIT).map { "$ARCHIVE/$it/android-auto/latest/certificate.pem" },
        keyUrls = listOf("main", ARCHIVE_COMMIT).map { "$ARCHIVE/$it/android-auto/latest/private-key.pem" },
        sha256 = null,
        family = Family(organisation = "CarService", rootOrganisation = "Google Automotive Link", rootSha256 = GOOGLE_AUTOMOTIVE_LINK),
        roleText = R.string.cert_role_newest,
        aboutText = R.string.cert_about_carservice,
    )

    /** In the order myCar offers and tries them: the recommended one first. */
    val entries = listOf(aasdk, revived, carService)

    fun entry(id: String): Entry? = entries.firstOrNull { it.id == id }

    /** Which catalog certificate [cert] is, by its fingerprint or its family; null for one from elsewhere. */
    fun idOf(cert: X509Certificate, list: List<Entry> = entries): String? {
        val sha = CertChecks.sha256(cert)
        return list.firstOrNull { it.sha256 == sha || it.family?.let { family -> CertChecks.inFamily(family, cert) } == true }?.id
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

    // --- a family's new certificate ---------------------------------------------------------

    /** How close to its end a family certificate is fetched anew: about the time its source takes to publish the next. */
    private const val REFRESH_BEFORE_MS = 30L * 24 * 60 * 60 * 1000
    private val refreshedThisRun = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Once a run (or [force]d, after a car found it expired): fetches anew a stored certificate its
     * source replaces once it's within a month of its end, and puts the new one in use where the
     * old one was. Without network it waits for the next start. Synchronous: off the main thread.
     */
    fun refreshIfDue(context: Context, force: Boolean = false) {
        if (!force && !refreshedThisRun.compareAndSet(false, true)) return
        if (refreshIfDue(context.filesDir, entries, CertDownloader::fetchText)) CertProvider.warm(context)
    }

    internal fun refreshIfDue(base: File, list: List<Entry>, fetch: (String) -> String, now: Long = System.currentTimeMillis()): Boolean {
        var changed = false
        for (entry in list) {
            if (entry.family == null || !stored(base, entry.id)) continue
            val old = runCatching { readCertificate(file(base, entry.id)) }.getOrNull() ?: continue
            if (old.notAfter.time - now > REFRESH_BEFORE_MS) continue
            val slot = CertProvider.downloadedSlot(base)
            val inUse = slot.isFile && runCatching { CertChecks.sha256(readCertificate(slot)) == CertChecks.sha256(old) }.getOrDefault(false)
            runCatching { fetchVerified(entry, fetch) }
                .onSuccess { fresh ->
                    if (fresh.certificate.notAfter.after(old.notAfter)) {
                        store(base, entry.id, fresh)
                        if (inUse) activate(base, entry.id)
                        changed = true
                        log.i("${entry.label}: the new one is here, valid until ${fresh.certificate.notAfter}")
                    } else {
                        log.i("${entry.label}: no newer one yet; this one runs out ${old.notAfter}")
                    }
                }
                .onFailure { log.w("${entry.label}: couldn't get the new one - ${it.message}") }
        }
        return changed
    }

    /** When the stored copy of [id] runs out, or null when there's none. Opens the file: off the main thread. */
    fun validUntil(context: Context, id: String): java.util.Date? =
        runCatching { readCertificate(file(context.filesDir, id)).notAfter }.getOrNull()

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
