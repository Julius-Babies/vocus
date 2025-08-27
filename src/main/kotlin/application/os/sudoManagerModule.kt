package dev.babies.application.os

import org.koin.dsl.module

val sudoManagerModule = module {
    single { SudoManager() }
}