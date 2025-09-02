package dev.babies.application.database.postgres.pgadmin

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import dev.babies.application.docker.AbstractDockerService
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain
import dev.babies.application.reverseproxy.RouterDestination
import dev.babies.application.reverseproxy.TraefikService
import dev.babies.applicationDirectory
import dev.babies.isDevelopment
import dev.babies.utils.docker.doesContainerExist
import dev.babies.utils.docker.isContainerRunning
import dev.babies.utils.docker.prepareImage
import dev.babies.utils.waitUntil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class Pgadmin : AbstractDockerService(
    containerName = "pgadmin" + if (isDevelopment) "_dev" else "",
    image = "dpage/pgadmin4:latest"
), KoinComponent {

    companion object {
        const val TRAEFIK_ROUTER_NAME = "pgadmin"
    }

    private val traefikService by inject<TraefikService>()
    private val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
    private val traefikConfig = traefikService.traefikDynamicConfig.resolve("pgadmin.yaml")

    val pgAdminDirectory = applicationDirectory
        .resolve("pgadmin")
        .apply { mkdirs() }

    override suspend fun createIfMissing() {
        val state = getState()
        if (state == State.Created) return
        if (state == State.Invalid) {
            println("The pgadmin differs from its required configuration. We'll attempt to remove it and create a new one.")
            println("If this fails, remove all containers that are using the $containerName network and try again.")
            dockerClient.removeContainerCmd(containerName).withForce(true).exec()
            println("Pgadmin $containerName removed.")
        }
        if (state == State.Missing) {
            println("Generating pgadmin $containerName")
        }

        dockerClient.prepareImage(image)
        if (dockerClient.doesContainerExist(containerName)) dockerClient.removeContainerCmd(containerName).withForce(true).exec()
        pgAdminDirectory.deleteRecursively()
        pgAdminDirectory.mkdirs()

        val dataBind = Bind.parse("${pgAdminDirectory.absolutePath}:/var/lib/pgadmin")

        dockerClient
            .createContainerCmd(image)
            .withName(containerName)
            .withEnv(
                "PGADMIN_DEFAULT_EMAIL=vocusdev@vocus.dev",
                "PGADMIN_DEFAULT_PASSWORD=vocus"
            )
            .withHostConfig(
                HostConfig()
                    .withBinds(dataBind)
                    .withNetworkMode(dockerNetwork.networkName)
            )
            .withLabels(
                mapOf("com.docker.compose.project" to COMPOSE_PROJECT_PREFIX)
            )
            .exec()
    }

    override suspend fun start() {
        if (dockerClient.isContainerRunning(containerName)) return
        if (getState() != State.Created) throw IllegalStateException()
        dockerClient.startContainerCmd(containerName).exec()

        waitUntil("$containerName to start") {
            dockerClient.isContainerRunning(containerName)
        }

        traefikService.addRouter(
            name = TRAEFIK_ROUTER_NAME,
            host = DomainBuilder(vocusDomain)
                .addSubdomain("infra")
                .addSubdomain("pgadmin")
                .toString(),
            file = traefikConfig,
            routerDestination = RouterDestination.ContainerPort(containerName, 80)
        )
    }

    override suspend fun stop() {
        if (getState() != State.Created) throw IllegalStateException()
        traefikConfig.delete()
        if (!dockerClient.isContainerRunning(containerName)) return
        dockerClient.stopContainerCmd(containerName).exec()
    }
}