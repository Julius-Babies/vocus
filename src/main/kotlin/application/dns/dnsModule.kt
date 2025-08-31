package dev.babies.application.dns

import org.koin.dsl.module

val dnsModule = module {
    single { AndroidDnsService() }
}