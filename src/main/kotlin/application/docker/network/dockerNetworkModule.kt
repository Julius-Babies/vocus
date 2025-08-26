package dev.babies.application.docker.network

import org.koin.core.qualifier.named
import org.koin.dsl.module

const val VOCUS_DOCKER_NETWORK_DI_KEY = "VocusDockerNetwork"

val dockerNetworkModule = module {
    single(named(VOCUS_DOCKER_NETWORK_DI_KEY)) {
        DockerNetwork(
            dockerClient = get(),
            networkName = "vocus"
        )
    }
}