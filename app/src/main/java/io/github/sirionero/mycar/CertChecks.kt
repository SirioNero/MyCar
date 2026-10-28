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
        val cert = PemKeyParser.parseCertificate(certificatePem)
        val got = sha256(cert)
        require(got == entry.sha256) { "not the expected certificate (SHA-256 $got)" }
        return cert
    }

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
