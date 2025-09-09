package dev.babies.application.database.rabbitmq.r4

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import dev.babies.application.database.rabbitmq.AbstractRabbitInstance
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain
import dev.babies.application.reverseproxy.RouterDestination
import dev.babies.application.reverseproxy.TraefikService
import dev.babies.isDevelopment
import dev.babies.utils.docker.doesContainerExist
import dev.babies.utils.docker.isContainerRunning
import dev.babies.utils.docker.prepareImage
import dev.babies.utils.docker.runCommand
import dev.babies.utils.waitUntil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class Rabbit4 : AbstractRabbitInstance(
    containerName = "rabbit4" + if (isDevelopment) "_dev" else "",
    image = "rabbitmq:4.1.3-management"
), KoinComponent {

    private val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
    private val traefikService by inject<TraefikService>()

    private val traefikRabbitMqManagementRouterFile = traefikService.traefikDynamicConfig.resolve("rabbitmq-management.yaml")

    private val rabbitPort = if (isDevelopment) 15672 else 5672
    override suspend fun getVHosts(): List<String> {
        dockerClient.runCommand(containerName, listOf("rabbitmqctl", "list_vhosts")).output.split("\n").let {
            return it.drop(1)
        }
    }

    override suspend fun createVHost(vHostName: String) {
        val createVHostResult = dockerClient.runCommand(containerName, listOf("rabbitmqctl", "add_vhost", vHostName))
        require(createVHostResult.exitCode == 0) {
            "Something went wrong while creating vHost $vHostName: ${createVHostResult.output}"
        }

        val setPermissionResult = dockerClient.runCommand(containerName, listOf("rabbitmqctl", "set_permissions", "-p", vHostName, "vocusdev", ".*", ".*", ".*"))
        require(setPermissionResult.exitCode == 0) {
            "Something went wrong while setting permissions for vHost $vHostName: ${setPermissionResult.output}"
        }
    }

    override suspend fun deleteVHost(vHostName: String) {
        val existing = getVHosts()
        if (!existing.contains(vHostName)) return
        val result = dockerClient.runCommand(containerName, listOf("rabbitmqctl", "delete_vhost", vHostName))
        require(result.exitCode == 0) {
            "Something went wrong while deleting vHost $vHostName: ${result.output}"
        }
    }

    override suspend fun createIfMissing() {
        val state = getState()
        if (state == State.Created) return
        if (state == State.Invalid) {
            println("The RabbitMQ container does exist but differs from its required configuration. We'll attempt to remove it and create a new one.")
            println("If this fails, remove all containers that are using the $containerName network and try again.")
            dockerClient.removeContainerCmd(containerName).withForce(true).exec()
            println("RabbitMQ $containerName removed.")
        }
        if (state == State.Missing) {
            println("Generating RabbitMQ $containerName")
        }

        dockerClient.prepareImage(image)
        if (dockerClient.doesContainerExist(containerName)) dockerClient.removeContainerCmd(containerName).withForce(true).exec()

        dataDirectory.deleteRecursively()
        dataDirectory.mkdirs()

        val exposedPort = ExposedPort.tcp(5672)
        val portBindings = Ports()
        portBindings.bind(exposedPort, Ports.Binding.bindPort(rabbitPort))

        val binds = Bind.parse("${dataDirectory.canonicalPath}:/var/lib/rabbitmq")

        dockerClient.createContainerCmd(image)
            .withName(containerName)
            .withEnv(
                "RABBITMQ_DEFAULT_USER=vocusdev",
                "RABBITMQ_DEFAULT_PASS=vocus"
            )
            .withHostConfig(
                HostConfig()
                    .withPortBindings(portBindings)
                    .withBinds(binds)
                    .withNetworkMode(dockerNetwork.networkName)
            )
            .withExposedPorts(exposedPort)
            .withLabels(
                mapOf("com.docker.compose.project" to COMPOSE_PROJECT_PREFIX)
            )
            .exec()
    }

    override suspend fun start() {
        if (getState() != State.Created) throw IllegalStateException()
        val domain = DomainBuilder(vocusDomain)
            .addSubdomain("infra")
            .addSubdomain("rabbitmq")
        traefikService.addRouter(
            name = "rabbitmq-management",
            file = traefikRabbitMqManagementRouterFile,
            host = domain.toString(),
            routerDestination = RouterDestination.ContainerPort(containerName, 15672, false)
        )
        if (!dockerClient.isContainerRunning(containerName)) {
            dockerClient.startContainerCmd(containerName).exec()
            waitUntil("RabbitMQ $containerName to start") {
                dockerClient.runCommand(
                    containerId = containerName,
                    command = listOf("rabbitmqctl", "status"),
                    suppressErrors = true
                ).exitCode == 0
            }
        }

    }

    override suspend fun stop() {
        if (getState() != State.Created) throw IllegalStateException()
        traefikRabbitMqManagementRouterFile.delete()
        if (!dockerClient.isContainerRunning(containerName)) return
        dockerClient.stopContainerCmd(containerName).exec()
    }
}