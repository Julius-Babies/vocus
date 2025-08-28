package dev.babies.application.reverseproxy

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import dev.babies.application.docker.AbstractDockerService
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.application.ssl.SslManager
import dev.babies.applicationDirectory
import dev.babies.utils.docker.doesContainerExist
import dev.babies.utils.docker.isContainerRunning
import dev.babies.utils.docker.prepareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class TraefikService : AbstractDockerService("traefik"), KoinComponent {

    private val dockerClient by inject<DockerClient>()
    private val sslManager by inject<SslManager>()
    private val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))

    private val traefikDirectory = applicationDirectory
        .resolve("traefik")
        .apply { mkdirs() }

    val traefikStaticConfig = traefikDirectory.resolve("traefik.yml")

    val image = "traefik:v3.5.0"
    override suspend fun createIfMissing() {
        val state = getState()
        if (state == State.Created) return
        if (state == State.Invalid) {
            println("The traefik differs from its required configuration. We'll attempt to remove it and create a new one.")
            println("If this fails, remove all containers that are using the $containerName network and try again.")
            dockerClient.removeContainerCmd(containerName).withForce(true).exec()
            println("Traefik $containerName removed.")
        }

        if (state == State.Missing) {
            println("Generating traefik $containerName")
        }

        dockerClient.prepareImage(image)
        if (dockerClient.doesContainerExist(containerName)) dockerClient.removeContainerCmd(containerName).withForce(true).exec()

        traefikDirectory.deleteRecursively()
        traefikDirectory.mkdirs()

        val exposedHttpPort = ExposedPort.tcp(80)
        val exposedDashboardPort = ExposedPort.tcp(8080)
        val exposedHttpsPort = ExposedPort.tcp(443)

        val portBindings = Ports()
        portBindings.bind(exposedHttpPort, Ports.Binding.bindPort(80))
        portBindings.bind(exposedDashboardPort, Ports.Binding.bindPort(8080))
        portBindings.bind(exposedHttpsPort, Ports.Binding.bindPort(443))

        val defaultConfigFile = {}::class.java.classLoader.getResourceAsStream("traefik/traefik.yml")
            ?.bufferedReader(Charsets.UTF_8)
            ?.readText()!!

        traefikStaticConfig.writeText(defaultConfigFile)

        val staticConfigurationBinding = Bind.parse("${traefikStaticConfig.absolutePath}:/etc/traefik/traefik.yml")
        val certificatesBinding = Bind.parse("${sslManager.sslDirectory.absolutePath}:/certificates:ro")

        dockerClient
            .createContainerCmd(image)
            .withName(containerName)
            .withHostConfig(
                HostConfig()
                    .withPortBindings(portBindings)
                    .withBinds(staticConfigurationBinding, certificatesBinding)
                    .withNetworkMode(dockerNetwork.networkName)
            )
            .withLabels(
                mapOf("com.docker.compose.project" to "vocus")
            )
            .exec()
    }

    override suspend fun start() {
        if (dockerClient.isContainerRunning(containerName)) return
        if (getState() != State.Created) throw IllegalStateException()
        dockerClient.startContainerCmd(containerName).exec()
    }

    override suspend fun stop() {
        if (getState() != State.Created) throw IllegalStateException()
        if (!dockerClient.isContainerRunning(containerName)) return
        dockerClient.stopContainerCmd(containerName).exec()
    }

    suspend fun getState(): State {
        withContext(Dispatchers.IO) {
            val databaseContainer = dockerClient
                .listContainersCmd()
                .withShowAll(true)
                .exec()
                .firstOrNull {containerName in it.names.map { name -> name.dropWhile { c -> c == '/' } } } ?: return@withContext State.Missing
            if (databaseContainer.image != image) return@withContext State.Invalid
            if (databaseContainer.labels["com.docker.compose.project"] != "vocus") return@withContext State.Invalid
            return@withContext null
        }?.let { return it }
        return State.Created
    }
}