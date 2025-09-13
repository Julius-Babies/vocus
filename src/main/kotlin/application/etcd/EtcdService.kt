package dev.babies.application.etcd

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import dev.babies.application.docker.AbstractDockerService
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.applicationDirectory
import dev.babies.isDevelopment
import dev.babies.utils.docker.doesContainerExist
import dev.babies.utils.docker.isContainerRunning
import dev.babies.utils.docker.prepareImage
import dev.babies.utils.waitUntil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class EtcdService: AbstractDockerService(
    containerName = "etcd" + if (isDevelopment) "_dev" else "",
    image = "registry.gitlab.jvbabi.es/vplanplus/infra/etcd:latest"
), KoinComponent {
    private val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
    private val dataDirectory = applicationDirectory
        .resolve("etcd")
        .resolve("data")
        .apply { mkdirs() }

    private val etcdPort = if (isDevelopment) 12379 else 2379

    override suspend fun createIfMissing() {
        dockerClient.prepareImage(image)
        if (isCreated()) return
        if (dockerClient.doesContainerExist(containerName)) dockerClient.removeContainerCmd(containerName).exec()

        val dataBinding = Bind.parse("${dataDirectory.canonicalPath}:/etcd/default.etcd")

        val exposedPort = ExposedPort.tcp(2379)
        val portBindings = Ports()
        portBindings.bind(exposedPort, Ports.Binding.bindPort(etcdPort))

        dockerClient.createContainerCmd(image)
            .withName(containerName)
            .withHostConfig(
                HostConfig()
                    .withPortBindings(portBindings)
                    .withBinds(dataBinding)
                    .withNetworkMode(dockerNetwork.networkName)
            )
            .withLabels(
                mapOf("com.docker.compose.project" to COMPOSE_PROJECT_PREFIX)
            )
            .exec()
    }

    override suspend fun start() {
        if (isRunning()) return
        dockerClient.startContainerCmd(containerName).exec()
        waitUntil("$containerName to start") {
            dockerClient.isContainerRunning(containerName)
        }
    }

    override suspend fun stop() {
        if (!isRunning()) return
        dockerClient.stopContainerCmd(containerName).exec()
    }
}