package dev.babies.application.ssl

import org.koin.dsl.module

val sslModule = module {
    single { SslManager() }
}