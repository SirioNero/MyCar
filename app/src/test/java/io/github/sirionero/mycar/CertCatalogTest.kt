package io.github.sirionero.mycar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyStore
import java.util.Base64

/**
 * The certificate catalog with a pretend network: the right file is kept, a wrong one is left out
 * for the next copy, and the slot [CertProvider] reads gets the one put in use. Certificates made
 * here, never the real ones.
 */
class CertCatalogTest {

    @get:Rule val folder = TemporaryFolder()

    private class Made(val certPem: String, val keyPem: String, val sha256: String)

    private fun entry(id: String, made: Made, places: Int = 2) = CertCatalog.Entry(
        id = id,
        label = id,
        project = "test",
        certUrls = (1..places).map { "https://$id/$it/cert" },
        keyUrls = (1..places).map { "https://$id/$it/key" },
        sha256 = made.sha256,
    )

    private fun fetcher(files: Map<String, String>): (String) -> String = { url ->
        files[url] ?: throw RuntimeException("404 $url")
    }

    private fun filesOf(id: String, made: Made, place: Int = 1) =
        mapOf("https://$id/$place/cert" to made.certPem, "https://$id/$place/key" to made.keyPem)

    @Test
    fun `keeps the certificate from the first place`() {
        val result = CertCatalog.fetchVerified(entry("a", a), fetcher(filesOf("a", a)))
        assertEquals(a.sha256, CertChecks.sha256(result.certificate))
    }

    @Test
    fun `a wrong file sends it to the next place`() {
        // The first place serves another certificate: left out, the second has the right one.
        val files = filesOf("a", b, place = 1) + filesOf("a", a, place = 2)
        val result = CertCatalog.fetchVerified(entry("a", a), fetcher(files))
        assertEquals(a.sha256, CertChecks.sha256(result.certificate))
    }

    @Test
    fun `a key that isn't the certificate's is left out`() {
        val files = mapOf("https://a/1/cert" to a.certPem, "https://a/1/key" to b.keyPem)
        assertThrows(IllegalStateException::class.java) {
            CertCatalog.fetchVerified(entry("a", a, places = 1), fetcher(files))
        }
    }

    @Test
    fun `one of two downloaded, the other said`() {
        val base = folder.newFolder()
        val downloads = CertCatalog.downloadAll(base, listOf(entry("a", a), entry("b", b)), fetcher(filesOf("a", a)))
        assertEquals(listOf("a"), downloads.ready)
        assertTrue(downloads.failed.containsKey("b"))
        assertTrue(CertCatalog.stored(base, "a"))
        assertFalse(CertCatalog.stored(base, "b"))
    }

    @Test
    fun `a stored certificate isn't downloaded again`() {
        val base = folder.newFolder()
        CertCatalog.downloadAll(base, listOf(entry("a", a)), fetcher(filesOf("a", a)))
        val again = CertCatalog.downloadAll(base, listOf(entry("a", a))) { throw RuntimeException("no network") }
        assertEquals(listOf("a"), again.ready)
    }

    @Test
    fun `the one put in use is the one in the slot`() {
        val base = folder.newFolder()
        CertCatalog.downloadAll(base, listOf(entry("a", a), entry("b", b)), fetcher(filesOf("a", a) + filesOf("b", b)))
        CertCatalog.activate(base, "b")
        assertEquals(b.sha256, CertChecks.sha256(CertCatalog.readCertificate(CertProvider.downloadedSlot(base))))
        CertCatalog.activate(base, "a")
        assertEquals(a.sha256, CertChecks.sha256(CertCatalog.readCertificate(CertProvider.downloadedSlot(base))))
    }

    @Test
    fun `the key in the slot opens with the slot's password`() {
        // CertProvider opens the downloaded slot, and its key, with this password.
        val base = folder.newFolder()
        CertCatalog.downloadAll(base, listOf(entry("a", a)), fetcher(filesOf("a", a)))
        CertCatalog.activate(base, "a")
        val keyStore = KeyStore.getInstance("PKCS12")
        CertProvider.downloadedSlot(base).inputStream().use { keyStore.load(it, "aaspike".toCharArray()) }
        assertNotNull(keyStore.getKey("phone", "aaspike".toCharArray()))
    }

    @Test
    fun `a certificate downloaded before the catalog is kept in it`() {
        val base = folder.newFolder()
        val list = listOf(entry("a", a))
        // As the download before the catalog left it: in the slot only.
        CertCatalog.downloadAll(base, list, fetcher(filesOf("a", a)))
        CertCatalog.activate(base, "a")
        CertCatalog.file(base, "a").delete()
        CertCatalog.adoptLegacy(base, list)
        assertTrue(CertCatalog.stored(base, "a"))
    }

    @Test
    fun `the real catalog is well formed`() {
        for (e in CertCatalog.entries) {
            assertTrue(e.id, e.sha256.matches(Regex("[0-9A-F]{64}")))
            assertEquals(e.id, e.certUrls.size, e.keyUrls.size)
            assertTrue(e.id, e.certUrls.all { it.startsWith("https://") })
        }
        assertEquals("aasdk", CertCatalog.entries.first().id)
    }

    companion object {
        // Two RSA keys for the whole class: making them is the slow part.
        private val a by lazy { make() }
        private val b by lazy { make() }

        private fun make(): Made {
            val identity = SelfSignedCert.generate()
            val key = identity.keyStore.getKey(SelfSignedCert.ALIAS, SelfSignedCert.PASSWORD)
            return Made(
                pem("CERTIFICATE", identity.certificate.encoded),
                pem("PRIVATE KEY", key.encoded),
                CertChecks.sha256(identity.certificate),
            )
        }

        private fun pem(type: String, der: ByteArray) =
            "-----BEGIN $type-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
                "\n-----END $type-----\n"
    }
}
