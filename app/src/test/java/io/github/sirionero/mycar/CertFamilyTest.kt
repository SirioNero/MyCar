package io.github.sirionero.mycar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A certificate its source replaces every few months (CarService, see CertCatalog.carService):
 * kept only when its root signed it, and fetched anew near its end. Made here, never the real ones.
 */
class CertFamilyTest {

    @get:Rule val folder = TemporaryFolder()

    private val family = CertCatalog.Family("CarService", "Test Root", CertChecks.sha256(root.certificate))

    private fun entry(id: String = "family") = CertCatalog.Entry(
        id = id,
        label = id,
        project = "test",
        certUrls = listOf("https://$id/cert"),
        keyUrls = listOf("https://$id/key"),
        sha256 = null,
        family = family,
    )

    /** The archive's way: the certificate, then its root, in one file. */
    private fun served(member: TestChain, withRoot: TestChain = root, id: String = "family"): (String) -> String = { url ->
        when (url) {
            "https://$id/cert" -> member.certPem + withRoot.certPem
            "https://$id/key" -> member.keyPem
            else -> throw RuntimeException("404 $url")
        }
    }

    @Test
    fun `one signed by its root is kept`() {
        val result = CertCatalog.fetchVerified(entry(), served(member))
        assertEquals(CertChecks.sha256(member.certificate), CertChecks.sha256(result.certificate))
    }

    @Test
    fun `one signed by another root is left out`() {
        assertThrows(IllegalStateException::class.java) { CertCatalog.fetchVerified(entry(), served(stranger, withRoot = otherRoot)) }
    }

    @Test
    fun `the right root in the file doesn't vouch for one it didn't sign`() {
        assertThrows(IllegalStateException::class.java) { CertCatalog.fetchVerified(entry(), served(stranger)) }
    }

    @Test
    fun `one naming another organisation is left out`() {
        assertThrows(IllegalStateException::class.java) { CertCatalog.fetchVerified(entry(), served(root.sign("Somebody else"))) }
    }

    @Test
    fun `it's told apart by its names`() {
        val list = listOf(entry())
        assertEquals("family", CertCatalog.idOf(member.certificate, list))
        assertNull(CertCatalog.idOf(otherRoot.sign("Somebody else").certificate, list))
    }

    @Test
    fun `near its end the new one comes, and the slot follows`() {
        val base = folder.newFolder()
        val old = root.sign("CarService", days = 10)
        CertCatalog.store(base, "family", PemKeyParser.build(old.certPem, old.keyPem))
        CertCatalog.activate(base, "family")
        assertTrue(CertCatalog.refreshIfDue(base, listOf(entry()), served(member)))
        val sha = CertChecks.sha256(member.certificate)
        assertEquals(sha, CertChecks.sha256(CertCatalog.readCertificate(CertCatalog.file(base, "family"))))
        assertEquals(sha, CertChecks.sha256(CertCatalog.readCertificate(CertProvider.downloadedSlot(base))))
    }

    @Test
    fun `far from its end nothing is fetched`() {
        val base = folder.newFolder()
        CertCatalog.store(base, "family", PemKeyParser.build(member.certPem, member.keyPem))
        assertFalse(CertCatalog.refreshIfDue(base, listOf(entry()), fetch = { throw AssertionError("fetched") }))
    }

    @Test
    fun `without a newer one the old one stays`() {
        val base = folder.newFolder()
        val old = root.sign("CarService", days = 10)
        CertCatalog.store(base, "family", PemKeyParser.build(old.certPem, old.keyPem))
        assertFalse(CertCatalog.refreshIfDue(base, listOf(entry()), served(old)))
        assertFalse(CertCatalog.refreshIfDue(base, listOf(entry()), fetch = { throw RuntimeException("no network") }))
        assertEquals(CertChecks.sha256(old.certificate), CertChecks.sha256(CertCatalog.readCertificate(CertCatalog.file(base, "family"))))
    }

    @Test
    fun `the real CarService entry pins Google's root`() {
        val real = CertCatalog.carService.family!!
        assertEquals("CarService", real.organisation)
        assertEquals("Google Automotive Link", real.rootOrganisation)
        assertTrue(CertCatalog.carService.certUrls.all { it.endsWith("/android-auto/latest/certificate.pem") })
    }

    companion object {
        // Making RSA keys is the slow part: once for the class.
        private val root by lazy { TestChain.root("Test Root") }
        private val otherRoot by lazy { TestChain.root("Test Root") }
        private val member by lazy { root.sign("CarService") }
        private val stranger by lazy { otherRoot.sign("CarService") }
    }
}
