package io.github.sirionero.mycar.ui

import android.content.Context
import io.github.sirionero.mycar.CertCatalog
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import java.text.DateFormat
import java.util.Date

/** What the UI says about the certificate a connection would present. */
data class CertSummary(
    val kind: CertProvider.Kind,
    val headline: String,
    val subject: String?,
    val issuer: String?,
    val validUntil: String?,
    /** Which certificate of [CertCatalog] it is, when it's one of them. */
    val catalogId: String? = null,
) {
    companion object {
        /**
         * The last [read], so Settings draws the certificate row at once instead of a second
         * later, which shifted everything under it. Null until the first read.
         */
        @Volatile var cached: CertSummary? = null
            private set

        /** Blocking (parses a key store): call from a background dispatcher. */
        fun read(context: Context): CertSummary = readNow(context).also { cached = it }

        private fun readNow(context: Context): CertSummary {
            val identity = CertProvider.loadSupplied(context)
                ?: return CertSummary(
                    CertProvider.Kind.SELF_SIGNED,
                    Words.get(R.string.cert_summary_none),
                    null, null, null,
                )
            val cert = identity.certificate
            val catalogId = CertCatalog.idOf(cert)
            val name = catalogId?.let { CertCatalog.entry(it)?.label } ?: friendlyName(cert.subjectX500Principal.name)
            val headline = when (identity.kind) {
                    CertProvider.Kind.IMPORTED -> R.string.cert_summary_imported
                    CertProvider.Kind.DOWNLOADED -> R.string.cert_summary_downloaded
                    CertProvider.Kind.ADB_STAGED -> R.string.cert_summary_staged
                    CertProvider.Kind.SELF_SIGNED -> R.string.cert_summary_self_signed
                }
            return CertSummary(
                identity.kind,
                Words.get(headline, name),
                cert.subjectX500Principal.name,
                cert.issuerX500Principal.name,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(cert.notAfter.time)),
                catalogId,
            )
        }

        private fun friendlyName(distinguishedName: String): String {
            for (key in listOf("CN", "O")) {
                Regex("(?:^|,)$key=([^,]+)").find(distinguishedName)?.let { return it.groupValues[1] }
            }
            return distinguishedName
        }
    }
}
