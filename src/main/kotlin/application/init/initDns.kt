package dev.babies.application.init

import dev.babies.application.dns.AndroidDnsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitDns : KoinComponent {
    val dnsService by inject<AndroidDnsService>()
}

suspend fun initDns() {
    InitDns.dnsService.createIfMissing()
    InitDns.dnsService.start()
}