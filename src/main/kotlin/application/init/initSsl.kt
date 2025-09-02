package dev.babies.application.init

import dev.babies.application.config.getConfig
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain
import dev.babies.application.ssl.SslManager
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
    InitSsl.sslManager.createOrUpdateCertificateForDomains("infra.$vocusDomain", vocusHosts)
}