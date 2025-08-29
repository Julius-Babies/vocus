package dev.babies.application.init

import dev.babies.application.config.getConfig
import dev.babies.application.ssl.SslManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitSsl: KoinComponent {
    val sslManager by inject<SslManager>()
}

fun initSsl() {
    val subdomains = getConfig().projects.associate { it.projectDomain to it.getAllProjectDomains() }
    subdomains.forEach { (projectName, additionalSubdomains) ->
        InitSsl.sslManager.createOrUpdateCertificateForDomains(projectName + ".local.vocus.dev", additionalSubdomains.map { it.lowercase() + ".${projectName.lowercase()}.local.vocus.dev" }.toSet())
    }
    InitSsl.sslManager.createOrUpdateCertificateForDomains("infra.local.vocus.dev", vocusHosts)
}