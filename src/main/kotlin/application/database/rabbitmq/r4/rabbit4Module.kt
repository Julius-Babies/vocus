package dev.babies.application.database.rabbitmq.r4

import org.koin.dsl.module

val rabbit4Module = module {
    single { Rabbit4() }
}