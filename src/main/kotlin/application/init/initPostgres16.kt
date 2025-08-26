package dev.babies.application.init

import com.github.dockerjava.api.DockerClient
import dev.babies.application.database.postgres.p16.Postgres16
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

private object InitPostgres16: KoinComponent {
    val dockerClient by inject<DockerClient>()
    val mainNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
}

suspend fun initPostgres16() {
    Postgres16(
        dockerClient = InitPostgres16.dockerClient,
        dockerNetworkName = InitPostgres16.mainNetwork.networkName
    ).createIfMissing()
}