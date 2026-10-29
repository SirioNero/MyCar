package io.github.sirionero.mycar

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

/**
 * What a downloaded certificate must pass before myCar keeps it: it is the file expected (its
 * SHA-256), the key belongs to it, RSA of at least 2048 bits, valid today, and a TLS handshake with
 * it works on the phone itself. A file that fails is left out, and [CertCatalog] tries the next copy.
 */
object CertChecks {
    private val log = MyCarLog.tagged("CERT")

    /** SHA-256 of the certificate (DER), in capitals without separators. */
    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }

    /** The certificate is the one [entry] expects. Checked before its key is even fetched. */
    fun checkFingerprint(entry: CertCatalog.Entry, certificatePem: String): X509Certificate {
        entry.family?.let { return checkFamily(it, certificatePem) }
        val cert = PemKeyParser.parseCertificate(certificatePem)
        val got = sha256(cert)
        require(got == entry.sha256) { "not the expected certificate (SHA-256 $got)" }
        return cert
    }

    /**
     * One of [family], which has no fixed SHA-256: it names the family's organisation, and its
     * signature is that of the root it must have, which the file carries after it.
     */
    fun checkFamily(family: CertCatalog.Family, certificatePem: String): X509Certificate {
        val certs = PemKeyParser.parseCertificates(certificatePem)
        val cert = requireNotNull(certs.firstOrNull()) { "no certificate in the file" }
        val root = requireNotNull(certs.drop(1).firstOrNull { sha256(it) == family.rootSha256 }) {
            "not with the expected root (SHA-256 ${certs.drop(1).joinToString { sha256(it) }})"
        }
        require(organisation(cert.subjectX500Principal) == family.organisation) { "not a ${family.organisation} certificate" }
        require(cert.issuerX500Principal == root.subjectX500Principal) { "not issued by the expected root" }
        // Throws when the root didn't sign it.
        cert.verify(root.publicKey)
        return cert
    }

    /** Whether [cert] belongs to [family], by its names: for telling which one is in use, not for trusting it. */
    fun inFamily(family: CertCatalog.Family, cert: X509Certificate): Boolean =
        organisation(cert.subjectX500Principal) == family.organisation &&
            organisation(cert.issuerX500Principal) == family.rootOrganisation

    /** The O= of a name, as RFC 2253 writes it. */
    internal fun organisation(name: javax.security.auth.x500.X500Principal): String? =
        Regex("(?:^|,)O=((?:\\\\.|[^,])*)").find(name.getName(javax.security.auth.x500.X500Principal.RFC2253))
            ?.groupValues?.get(1)?.replace("\\", "")

    /** Every check, in order; throws at the first that fails. */
    fun verify(entry: CertCatalog.Entry, certificatePem: String, privateKeyPem: String): PemKeyParser.Result {
        checkFingerprint(entry, certificatePem)
        val result = PemKeyParser.build(certificatePem, privateKeyPem)
        val cert = result.certificate
        val bits = (cert.publicKey as? RSAPublicKey)?.modulus?.bitLength() ?: 0
        require(bits >= 2048) { "the key is too short ($bits bits)" }
        cert.checkValidity()
        log.i("${entry.label}: valid from ${cert.notBefore} to ${cert.notAfter}")
        val identity = CertProvider.Identity(
            result.keyStore, cert, CharArray(0), "${entry.label} (download check)", CertProvider.Kind.DOWNLOADED,
        )
        check(TlsSelfTest.run(identity)) { "a TLS handshake with it failed on this phone" }
        return result
    }
}
