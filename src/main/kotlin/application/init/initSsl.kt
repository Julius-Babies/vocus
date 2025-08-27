package dev.babies.application.init

import dev.babies.application.config.getConfig
import dev.babies.application.ssl.SslManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitSsl: KoinComponent {
    val sslManager by inject<SslManager>()
}

fun initSsl() {
    val subdomains = getConfig().projects.associate { it.name to it.additionalSubdomains }
    subdomains.forEach { (projectName, additionalSubdomains) ->
        InitSsl.sslManager.createOrUpdateCertificateForDomains(projectName.lowercase() + ".vocus.local.dev", additionalSubdomains.map { it.lowercase() + ".${projectName.lowercase()}.vocus.local.dev" }.toSet())
    }
}