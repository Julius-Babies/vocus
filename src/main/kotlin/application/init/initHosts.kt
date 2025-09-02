package dev.babies.application.init

import dev.babies.application.config.getConfig
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.HostsManager
import dev.babies.application.os.host.vocusDomain
import dev.babies.utils.gray
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitHosts: KoinComponent {
    val hostsManager by inject<HostsManager>()
}

val vocusHosts = setOf(
    DomainBuilder(vocusDomain).addSubdomain("infra").addSubdomain("pgadmin").toString(),
    DomainBuilder(vocusDomain).addSubdomain("infra").addSubdomain("traefik").toString(),
    DomainBuilder(vocusDomain).addSubdomain("infra").addSubdomain("rabbitmq").toString(),
    DomainBuilder(vocusDomain).addSubdomain("infra").addSubdomain("mongo").toString(),
)

fun initHosts() {
    val hosts = getConfig().projects
        .flatMap { projectConfig -> projectConfig.getAllProjectDomains() }
        .map { DomainBuilder(it).buildAsSubdomain(suffix = vocusDomain, skipIfSuffixAlreadyPresent = true) }
        .plus(vocusHosts)
        .toSet()
    val existing = InitHosts.hostsManager.getHosts()
    val newsHosts = hosts - existing
    if (newsHosts.isNotEmpty()) {
        println(gray("We need to add these hosts: ${newsHosts.joinToString()}"))
        InitHosts.hostsManager.addHost(newsHosts)
    }

    val removed = existing - hosts
    if (removed.isNotEmpty()) {
        println(gray("We need to remove these hosts: ${removed.joinToString()}"))
        InitHosts.hostsManager.removeHost(removed)
    }
}