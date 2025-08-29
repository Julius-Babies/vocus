package dev.babies.application.database.postgres.pgadmin

import org.koin.dsl.module

val pgadminModule = module {
    single { Pgadmin() }
}