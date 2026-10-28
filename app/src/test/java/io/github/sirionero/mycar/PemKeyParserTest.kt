package io.github.sirionero.mycar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.interfaces.RSAPrivateCrtKey
import java.util.Base64

/**
 * The two key formats the downloadable certificates come in: PKCS#1 ("BEGIN RSA PRIVATE KEY",
 * aasdk) and PKCS#8 ("BEGIN PRIVATE KEY", Headunit Revived). Keys made here, never real ones.
 */
class PemKeyParserTest {

    private val identity = SelfSignedCert.generate()
    private val key = identity.keyStore.getKey(SelfSignedCert.ALIAS, SelfSignedCert.PASSWORD) as RSAPrivateCrtKey
    private val certPem = pem("CERTIFICATE", identity.certificate.encoded)

    @Test
    fun `reads a PKCS#1 key`() {
        val result = PemKeyParser.build(certPem, pem("RSA PRIVATE KEY", pkcs1(key)))
        assertEquals(identity.certificate, result.certificate)
        assertTrue(result.keyStore.isKeyEntry("phone"))
    }

    @Test
    fun `reads a PKCS#8 key`() {
        val result = PemKeyParser.build(certPem, pem("PRIVATE KEY", key.encoded))
        assertEquals(identity.certificate, result.certificate)
        assertTrue(result.keyStore.isKeyEntry("phone"))
    }

    @Test
    fun `refuses a key that isn't the certificate's`() {
        val other = SelfSignedCert.generate().keyStore.getKey(SelfSignedCert.ALIAS, SelfSignedCert.PASSWORD)
        assertThrows(IllegalArgumentException::class.java) {
            PemKeyParser.build(certPem, pem("PRIVATE KEY", other.encoded))
        }
    }

    @Test
    fun `says so when the key has a password`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            PemKeyParser.build(certPem, pem("ENCRYPTED PRIVATE KEY", ByteArray(16)))
        }
        assertTrue(e.message.orEmpty().contains("password"))
    }

    private fun pem(type: String, der: ByteArray) =
        "-----BEGIN $type-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
            "\n-----END $type-----\n"

    /** The PKCS#1 form of [k]: the nine integers of RSAPrivateKey. */
    private fun pkcs1(k: RSAPrivateCrtKey): ByteArray {
        val ints = listOf(
            BigInteger.ZERO, k.modulus, k.publicExponent, k.privateExponent, k.primeP, k.primeQ,
            k.primeExponentP, k.primeExponentQ, k.crtCoefficient,
        )
        return tlv(0x30, ints.map { tlv(0x02, it.toByteArray()) }.reduce { a, b -> a + b })
    }

    private fun tlv(tag: Int, body: ByteArray): ByteArray {
        val n = body.size
        val length = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }
        return byteArrayOf(tag.toByte()) + length + body
    }
}
