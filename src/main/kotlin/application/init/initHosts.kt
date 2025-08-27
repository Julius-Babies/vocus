package dev.babies.application.init

import dev.babies.application.config.getConfig
import dev.babies.application.os.host.HostsManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitHosts: KoinComponent {
    val hostsManager by inject<HostsManager>()
}

fun initHosts() {
    val hosts = getConfig().projects.flatMap { listOf(it.name.lowercase()) + it.additionalSubdomains.map { sub -> "${sub.lowercase()}.${it.name.lowercase()}" } }.toSet()
    val existing = InitHosts.hostsManager.getHosts()
    InitHosts.hostsManager.addHost(hosts)

    val removed = existing - hosts
    InitHosts.hostsManager.removeHost(removed)
}