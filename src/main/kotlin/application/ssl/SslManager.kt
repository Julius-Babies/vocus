package dev.babies.application.ssl

import dev.babies.applicationDirectory
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*

class SslManager {

    val sslDirectory = applicationDirectory.resolve("ssl").apply { mkdirs() }

    fun getRootCaCertificateAndKey(): Pair<X509Certificate, PrivateKey> {
        val rootCaCertFile = sslDirectory.resolve("root-ca.crt")
        val rootCaKeyFile = sslDirectory.resolve("root-ca.key")
        if (!rootCaCertFile.exists() || !rootCaKeyFile.exists()) {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(4096, SecureRandom())
            val keyPair = keyGen.generateKeyPair()
            val privateKey = keyPair.private
            val publicKey = keyPair.public

            val issuer = X500Name("CN=VocusRoot, O=VocusDev, C=DE")
            val now = Date()
            val validity = 365L * 24 * 60 * 60 * 1000 * 50 // 50 Years
            val notAfter = Date(now.time + validity)
            val serial = BigInteger(64, SecureRandom())

            val certBuilder = JcaX509v3CertificateBuilder(
                issuer,
                serial,
                now,
                notAfter,
                issuer,
                publicKey
            )
            writeCertificateAndKeyToFiles(privateKey, certBuilder, rootCaCertFile, rootCaKeyFile, privateKey)
        }

        val certificate = FileReader(rootCaCertFile).use { reader ->
            PEMParser(reader).use { pemParser ->
                val certHolder = pemParser.readObject() as X509CertificateHolder
                JcaX509CertificateConverter().getCertificate(certHolder)
            }
        }
        val privateKey = FileReader(rootCaKeyFile).use { reader ->
            PEMParser(reader).use { pemParser ->
                when (val obj = pemParser.readObject()) {
                    is org.bouncycastle.openssl.PEMKeyPair -> {
                        val kp = JcaPEMKeyConverter().getKeyPair(obj)
                        kp.private
                    }
                    is PEMEncryptedKeyPair -> {
                        throw IllegalStateException("Verschlüsselte Schlüssel werden nicht unterstützt.")
                    }
                    is PrivateKey -> obj
                    else -> throw IllegalStateException("Unbekanntes Schlüsselformat.")
                }
            }
        }
        return certificate to privateKey
    }

    fun createOrUpdateCertificateForDomains(commonName: String, alternativeNames: Set<String>) {
        require(commonName.isNotBlank()) { "commonName cannot be blank" }
        require(alternativeNames.all { it.isNotBlank() }) { "alternativeNames cannot contain blank values" }
        require(!commonName.startsWith("*")) { "commonName cannot be a wildcard" }

        val certificateDirectory = sslDirectory.resolve(commonName).apply { mkdirs() }
        val certificateFile = certificateDirectory.resolve("cert.crt")
        val keyFile = certificateDirectory.resolve("cert.key")

        val allDomains = setOf(commonName) + alternativeNames
        var needsGeneration = true
        if (certificateFile.exists() && keyFile.exists()) {
            try {
                val cert = FileReader(certificateFile).use { reader ->
                    PEMParser(reader).use { pemParser ->
                        val certHolder = pemParser.readObject() as X509CertificateHolder
                        JcaX509CertificateConverter().getCertificate(certHolder)
                    }
                }
                val san = cert.subjectAlternativeNames?.mapNotNull {
                    it[1]?.toString()
                }?.toSet() ?: emptySet()
                if (allDomains.all { it in san }) {
                    needsGeneration = false
                }
            } catch (_: Exception) {
                needsGeneration = true
            }
        }
        if (needsGeneration) {
            val (caCert, caKey) = getRootCaCertificateAndKey()
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(4096, SecureRandom())
            val keyPair = keyGen.generateKeyPair()
            val privateKey = keyPair.private
            val publicKey = keyPair.public

            val issuer = X500Name(caCert.subjectX500Principal.name)
            val subject = X500Name("CN=$commonName, O=VocusDev, C=DE")
            val now = Date()
            val validity = 365L * 24 * 60 * 60 * 1000 * 5 // 5 Jahre
            val notAfter = Date(now.time + validity)
            val serial = BigInteger(64, SecureRandom())

            val certBuilder = JcaX509v3CertificateBuilder(
                issuer,
                serial,
                now,
                notAfter,
                subject,
                publicKey
            )
            val sanList = allDomains.map {
                GeneralName(GeneralName.dNSName, it)
            }
            val sanSeq = org.bouncycastle.asn1.x509.GeneralNames(sanList.toTypedArray())
            certBuilder.addExtension(
                Extension.subjectAlternativeName,
                false,
                sanSeq
            )
            writeCertificateAndKeyToFiles(caKey, certBuilder, certificateFile, keyFile, privateKey)
        }
    }

    private fun writeCertificateAndKeyToFiles(
        caKey: PrivateKey,
        certBuilder: JcaX509v3CertificateBuilder,
        certificateFile: File,
        keyFile: File,
        privateKey: PrivateKey?
    ) {
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKey)
        val certHolder = certBuilder.build(signer)
        val certificate = JcaX509CertificateConverter().getCertificate(certHolder)

        FileWriter(certificateFile).use { fw ->
            JcaPEMWriter(fw).use { pemWriter ->
                pemWriter.writeObject(certificate)
            }
        }
        FileWriter(keyFile).use { fw ->
            JcaPEMWriter(fw).use { pemWriter ->
                pemWriter.writeObject(privateKey)
            }
        }
    }
}