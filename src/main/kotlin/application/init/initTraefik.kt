package dev.babies.application.init

import dev.babies.application.reverseproxy.TraefikService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitTraefik : KoinComponent {
    val traefikService by inject<TraefikService>()
}

suspend fun initTraefik() {
    InitTraefik.traefikService.createIfMissing()
    InitTraefik.traefikService.start()
}