package dev.babies.application.main

import dev.babies.application.database.postgres.p16.Postgres16
import dev.babies.application.reverseproxy.TraefikService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object Main : KoinComponent {
    val traefikService by inject<TraefikService>()
    val postgres16 by inject<Postgres16>()
}

suspend fun start() {
    Main.postgres16.start()
    Main.traefikService.start()
}