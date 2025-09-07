package dev.babies.application.dns

import com.github.dockerjava.api.model.*
import dev.babies.application.config.getConfig
import dev.babies.application.docker.AbstractDockerService
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.application.init.vocusHosts
import dev.babies.application.os.host.vocusDomain
import dev.babies.applicationDirectory
import dev.babies.isDevelopment
import dev.babies.utils.docker.doesContainerExist
import dev.babies.utils.docker.prepareImage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * This is used to resolve local DNS names for the Android emulator.
 */
class AndroidDnsService: AbstractDockerService(
    containerName = "android_dns" + if (isDevelopment) "_dev" else "",
    image = "ghcr.io/zyrakq/unbound:latest"
), KoinComponent {
    private val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
    private val configDirectory = applicationDirectory.resolve("android-dns").apply { mkdirs() }
    private val configFile = configDirectory.resolve("unbound.conf")

    override suspend fun createIfMissing() {
        writeConfigFile()
        if (isCreated()) return
        dockerClient.prepareImage(image)
        if (dockerClient.doesContainerExist(containerName)) dockerClient.removeContainerCmd(containerName).exec()

        val exposedDnsPortUdp = ExposedPort(53, InternetProtocol.UDP)
        val exposedDnsPortTcp = ExposedPort(53, InternetProtocol.TCP)
        val portBindings = Ports()
        portBindings.bind(exposedDnsPortUdp, Ports.Binding.bindPort(53))
        portBindings.bind(exposedDnsPortTcp, Ports.Binding.bindPort(53))

        val configFileBinding = Bind.parse("${configFile.canonicalPath}:/etc/unbound/unbound.conf:ro")

        dockerClient.createContainerCmd(image)
            .withName(containerName)
            .withExposedPorts(exposedDnsPortUdp, exposedDnsPortTcp)
            .withHostConfig(
                HostConfig()
                    .withPortBindings(portBindings)
                    .withBinds(configFileBinding)
                    .withNetworkMode(dockerNetwork.networkName)
            )
            .withLabels(
                mapOf("com.docker.compose.project" to COMPOSE_PROJECT_PREFIX)
            )
            .exec()
    }

    override suspend fun start() {
        writeConfigFile()
        if (isRunning()) stop()
        dockerClient.startContainerCmd(containerName).exec()
    }

    override suspend fun stop() {
        if (!isRunning()) return
        dockerClient.stopContainerCmd(containerName).exec()
    }

    private fun writeConfigFile() {
        configFile.delete()
        val hosts = (getConfig().projects.flatMap { it.getAllProjectDomains() } + vocusHosts).toSet()

        val content = {}::class.java.classLoader.getResourceAsStream("unbound-android/unbound.conf")
            ?.bufferedReader(Charsets.UTF_8)
            ?.readText()!!
            .replace(
                "    LOCAL_ZONES", buildString {
                    append("local-zone: \"$vocusDomain\" static\n")
                    append("local-zone: \"$vocusDomain.\" static\n")
                    hosts.forEach { hosts ->
                        append("local-data: \"$hosts. IN A 10.0.2.2\"\n")
                    }
                }.prependIndent("    ")
            )

        configFile.writeText(content)
    }
}