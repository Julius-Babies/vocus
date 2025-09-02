package dev.babies.application.ssl

import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain
import dev.babies.applicationDirectory
import dev.babies.isDevelopment
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.*
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class SslManager {
    val sslDirectory = applicationDirectory.resolve("ssl").apply { mkdirs() }

    /**
     * Loads a PrivateKey from a PEM file
     */
    private fun loadPrivateKey(pemFile: File): PrivateKey = FileInputStream(pemFile).use { fis ->
        PEMParser(InputStreamReader(fis)).use { parser ->
            val obj = parser.readObject()
            val converter = JcaPEMKeyConverter().setProvider("BC")
            when (obj) {
                is PEMKeyPair -> converter.getPrivateKey(obj.privateKeyInfo)
                is PrivateKeyInfo -> converter.getPrivateKey(obj)
                is org.bouncycastle.openssl.PEMEncryptedKeyPair -> throw IllegalStateException("Encrypted key not supported")
                else -> throw IllegalStateException("Unsupported key PEM object: ${obj?.javaClass}")
            }
        }
    }

    /**
     * Loads an X509 certificate from a PEM file
     */
    private fun loadCertificate(pemFile: File): X509Certificate? {
        if (!pemFile.exists()) return null
        return FileInputStream(pemFile).use { fis ->
            PEMParser(InputStreamReader(fis)).use { parser ->
                when (val obj = parser.readObject()) {
                    is X509CertificateHolder -> JcaX509CertificateConverter().setProvider("BC").getCertificate(obj)
                    else -> throw IllegalStateException("Unsupported cert PEM object: ${obj?.javaClass}")
                }
            }
        }
    }

    /**
     * Writes an object to a PEM file
     */
    private fun writePem(file: File, obj: Any) {
        FileWriter(file).use { fw ->
            JcaPEMWriter(fw).use { pw -> pw.writeObject(obj) }
        }
    }

    // --- Main functions ---

    /**
     * Lists all existing certificates
     */
    fun getCertificates(): List<String> {
        return sslDirectory.listFiles()
            ?.filter { it.isDirectory && it.resolve("cert.crt").exists() && it.resolve("cert.key").exists() }
            ?.map { it.name }
            .orEmpty()
    }

    /**
     * Generates or loads Root CA and key
     */
    fun getRootCaCertificateAndKey(): Pair<X509Certificate, PrivateKey> {
        Security.addProvider(BouncyCastleProvider())
        val certFile = sslDirectory.resolve("root-ca.crt")
        val keyFile = sslDirectory.resolve("root-ca.key")

        if (!certFile.exists() || !keyFile.exists()) {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
            keyPairGenerator.initialize(4096)
            val keyPair = keyPairGenerator.generateKeyPair()

            val startDate = Date()
            val endDate = Date(startDate.time + 10L * 365 * 24 * 60 * 60 * 1000)

            val cn = if (isDevelopment) "VocusRoot Dev" else "VocusRoot"
            val distinguishedName = "CN=$cn, O=VocusDev, C=DE"
            val x500Name = X500Name(distinguishedName)
            val serialNumber = BigInteger.valueOf(System.currentTimeMillis())

            val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
                x500Name,
                serialNumber,
                startDate,
                endDate,
                x500Name,
                keyPair.public
            )

            // Mark root CA as CA
            certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            certBuilder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))

            val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)
            val rootHolder = certBuilder.build(signer)
            val rootCertificate = JcaX509CertificateConverter().setProvider("BC").getCertificate(rootHolder)

            writePem(certFile, rootCertificate)
            writePem(keyFile, keyPair.private)
        }

        val privateKey = loadPrivateKey(keyFile)
        val certificate = loadCertificate(certFile)!!
        return certificate to privateKey
    }

    /**
     * Creates or updates a certificate for the given domains
     */
    fun createOrUpdateCertificateForDomains(commonName: String, alternativeNames: Set<String>) {
        require(commonName.isNotBlank() && !commonName.startsWith("*"))
        require(alternativeNames.all { it.isNotBlank() })
        Security.addProvider(BouncyCastleProvider())

        val certDir = sslDirectory.resolve(commonName).apply { mkdirs() }
        val certFile = certDir.resolve("cert.crt")
        val keyFile = certDir.resolve("cert.key")
        val fullchainFile = certDir.resolve("fullchain.pem")

        val allDomains = setOf(commonName) + alternativeNames
        var needsGeneration = true

        if (certFile.exists() && keyFile.exists()) {
            try {
                val cert = loadCertificate(certFile)
                val san = cert!!.subjectAlternativeNames?.mapNotNull { it[1]?.toString() }?.toSet() ?: emptySet()
                if (allDomains.all { it in san }) needsGeneration = false
            } catch (_: Exception) { needsGeneration = true }
        }

        if (needsGeneration) {
            certFile.delete()
            keyFile.delete()
            val (rootCert, rootKey) = getRootCaCertificateAndKey()
            val rootHolder = X509CertificateHolder(rootCert.encoded)

            fun contentSignerFor(privateKey: PrivateKey): ContentSigner {
                val sigAlg = when (privateKey.algorithm) {
                    "RSA" -> "SHA256withRSA"
                    "EC", "ECDSA" -> "SHA256withECDSA"
                    else -> "SHA256withRSA"
                }
                return JcaContentSignerBuilder(sigAlg).setProvider(BouncyCastleProvider()).build(privateKey)
            }

            val kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()

            val subject = X500Name("CN=$commonName, O=VocusDev, C=DE")
            val issuer = rootHolder.subject
            val serial = BigInteger(64, SecureRandom())
            val notBefore = Date.from(Instant.now().minus(1, ChronoUnit.DAYS))
            val notAfter = Date.from(Instant.now().plus(365, ChronoUnit.DAYS))

            val certBuilder = JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                kp.public
            )

            certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            val keyUsage = KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
            certBuilder.addExtension(Extension.keyUsage, true, keyUsage)
            val eku = ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
            certBuilder.addExtension(Extension.extendedKeyUsage, false, eku)

            val names = mutableListOf<GeneralName>()
            for (sd in allDomains) {
                if (sd.isNotBlank()) {
                    val domain = DomainBuilder(sd).buildAsSubdomain(skipIfSuffixAlreadyPresent = true, suffix = vocusDomain)
                    names.add(GeneralName(GeneralName.dNSName, domain))
                }
            }
            val san = GeneralNames(names.toTypedArray())
            certBuilder.addExtension(Extension.subjectAlternativeName, false, san)

            val signer: ContentSigner = contentSignerFor(rootKey)
            val holder: X509CertificateHolder = certBuilder.build(signer)

            writePem(certFile, holder)
            val privateKeyInfo = PrivateKeyInfo.getInstance(kp.private.encoded)
            writePem(keyFile, privateKeyInfo)

            FileWriter(fullchainFile).use { fw ->
                JcaPEMWriter(fw).use { pw ->
                    pw.writeObject(holder)
                    pw.writeObject(rootHolder)
                }
            }

            verifyCertificateChain(fullchainFile.absolutePath, sslDirectory.resolve("root-ca.crt").absolutePath)
        }
    }

    /**
     * Validates the certificate chain against the Root CA
     */
    fun verifyCertificateChain(fullchainPath: String, rootCertPath: String) {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val fullChainCerts = FileInputStream(fullchainPath).use { cf.generateCertificates(it) }
            val rootCert = FileInputStream(rootCertPath).use { cf.generateCertificate(it) as X509Certificate }

            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(null, null)
            ks.setCertificateEntry("root", rootCert)

            val pkixParams = PKIXParameters(ks)
            pkixParams.isRevocationEnabled = false

            val certPath: CertPath = cf.generateCertPath(fullChainCerts.toList())

            val validator = CertPathValidator.getInstance("PKIX")
            validator.validate(certPath, pkixParams)
        } catch (e: Exception) {
            throw e
        }
    }
}