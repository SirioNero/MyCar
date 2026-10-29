package io.github.sirionero.mycar

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Certificates made for the tests the way Android Auto's own are: a root, and certificates it
 * signs, each naming an organisation. Never the real ones.
 */
internal class TestChain private constructor(val certificate: X509Certificate, private val keys: KeyPair) {

    val certPem: String get() = pem("CERTIFICATE", certificate.encoded)
    val keyPem: String get() = pem("PRIVATE KEY", keys.private.encoded)

    /** A certificate naming [organisation], issued and signed by this one, valid for [days] from yesterday. */
    fun sign(organisation: String, days: Long = 3650): TestChain {
        val own = newKeys()
        val cert = build(certificate.subjectX500Principal.encoded, name(organisation), own.public, keys.private, days)
        return TestChain(cert, own)
    }

    companion object {
        private const val DAY = 86_400_000L

        /** A root naming [organisation], signed by itself. */
        fun root(organisation: String): TestChain {
            val keys = newKeys()
            val name = name(organisation)
            return TestChain(build(name, name, keys.public, keys.private, 3650), keys)
        }

        private fun newKeys(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        private val OID_SHA256_RSA = byteArrayOf(
            0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B,
        )
        private val OID_O = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x0A)

        private fun build(issuer: ByteArray, subject: ByteArray, key: PublicKey, signer: PrivateKey, days: Long): X509Certificate {
            val algorithmId = seq(OID_SHA256_RSA, tlv(0x05, ByteArray(0)))
            val now = System.currentTimeMillis()
            val tbs = seq(
                tlv(0xA0, integer(BigInteger.valueOf(2))),
                integer(BigInteger(64, SecureRandom()).add(BigInteger.ONE)),
                algorithmId,
                issuer,
                seq(utcTime(Date(now - DAY)), utcTime(Date(now + days * DAY))),
                subject,
                key.encoded,
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(signer)
                update(tbs)
                sign()
            }
            val der = seq(tbs, algorithmId, bitString(signature))
            return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }

        private fun name(organisation: String) = seq(tlv(0x31, seq(OID_O, tlv(0x0C, organisation.toByteArray(Charsets.UTF_8)))))

        private fun length(n: Int): ByteArray {
            if (n < 0x80) return byteArrayOf(n.toByte())
            var value = n
            val out = ArrayList<Byte>()
            while (value > 0) {
                out.add(0, (value and 0xFF).toByte())
                value = value ushr 8
            }
            return byteArrayOf((0x80 or out.size).toByte()) + out.toByteArray()
        }

        private fun tlv(tag: Int, body: ByteArray) = byteArrayOf(tag.toByte()) + length(body.size) + body

        private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.reduce { a, b -> a + b })

        private fun integer(v: BigInteger) = tlv(0x02, v.toByteArray())

        private fun bitString(body: ByteArray) = tlv(0x03, byteArrayOf(0x00) + body)

        private fun utcTime(date: Date): ByteArray {
            val format = SimpleDateFormat("yyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return tlv(0x17, (format.format(date) + "Z").toByteArray(Charsets.US_ASCII))
        }

        fun pem(type: String, der: ByteArray) =
            "-----BEGIN $type-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
                "\n-----END $type-----\n"
    }
}
