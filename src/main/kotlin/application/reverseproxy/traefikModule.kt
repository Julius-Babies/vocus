package dev.babies.application.reverseproxy

import org.koin.dsl.module

val traefikModule = module {
    single { TraefikService() }
}