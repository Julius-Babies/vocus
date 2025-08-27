package dev.babies.application.os.host

import org.koin.dsl.module

val hostsManagerModule = module {
    single { HostsManager() }
}