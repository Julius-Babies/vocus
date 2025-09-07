package dev.babies.application.init

import com.sun.jna.Platform
import dev.babies.application.config.getConfig
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain
import dev.babies.application.ssl.SslManager
import dev.babies.utils.green
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitSsl : KoinComponent {
    val sslManager by inject<SslManager>()
}

fun initSsl() {
    val subdomains = getConfig().projects.associate { it.projectDomain to it.getAllProjectDomains() }
    subdomains.forEach { (projectName, additionalSubdomains) ->
        val domain = projectName.buildAsSubdomain(skipIfSuffixAlreadyPresent = true, suffix = vocusDomain)
        val additionalDomains = additionalSubdomains
            .map { DomainBuilder(it).buildAsSubdomain(skipIfSuffixAlreadyPresent = true, suffix = vocusDomain) }
            .toSet()

        InitSsl.sslManager.createOrUpdateCertificateForDomains(domain, additionalDomains)
    }

    getConfig().projects.forEach { projectConfig ->
        projectConfig.modules.forEach { moduleConfig ->
            InitSsl.sslManager.createCertificate(
                commonName = projectConfig.projectDomain.addSubdomain(DomainBuilder.nameToDomain(moduleConfig.key)).buildAsSubdomain(skipIfSuffixAlreadyPresent = true, suffix = vocusDomain),
                alternativeNames = emptySet(),
                destinationDirectory = InitSsl.sslManager.sslDirectory
                    .resolve("service")
                    .resolve("${DomainBuilder.nameToDomain(projectConfig.name)}.${DomainBuilder.nameToDomain(moduleConfig.key)}")
            )
        }
    }

    InitSsl.sslManager.createCertificate(
        "developer",
        emptySet(),
        InitSsl.sslManager.sslDirectory.resolve("developer")
    ).let { result ->
        if (!result.created) return@let
        println(green("Generated developer certificate."))
        if (Platform.isMac()) {
            println("Add the ${InitSsl.sslManager.sslDirectory.resolve("root-ca.crt").canonicalPath} to your keychain and trust it.")
            println("Then, add the ${InitSsl.sslManager.sslDirectory.resolve("developer").resolve("bundle.p12").canonicalPath} to your keychain as well to connect to the mTLS services.")
        } else {
            println("Add the ${InitSsl.sslManager.sslDirectory.resolve("root-ca.crt").canonicalPath} to your trusted certificates.")
            println("Then, add the ${InitSsl.sslManager.sslDirectory.resolve("developer").resolve("bundle.p12").canonicalPath} to your local certificates to connect to the mTLS services.")
        }
    }

    InitSsl.sslManager.createOrUpdateCertificateForDomains("infra.$vocusDomain", vocusHosts)
}