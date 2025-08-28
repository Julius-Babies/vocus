package dev.babies.application.database.postgres.p16

import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import org.koin.core.qualifier.named
import org.koin.dsl.module

val postgres16Module = module {
    single { Postgres16(
        dockerClient = get(),
        dockerNetworkName = get<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY)).networkName
    ) }
}