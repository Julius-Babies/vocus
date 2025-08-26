package dev.babies.application.init

import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

private object UpdateDockerNetwork : KoinComponent {
    val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
}

suspend fun updateDockerNetwork() {
    val networkState = UpdateDockerNetwork.dockerNetwork.getState()
    if (networkState == DockerNetwork.State.Created) return
    UpdateDockerNetwork.dockerNetwork.createIfMissing()
}