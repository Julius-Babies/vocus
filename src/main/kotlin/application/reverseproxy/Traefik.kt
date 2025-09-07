package dev.babies.application.reverseproxy

import com.charleskorn.kaml.Yaml
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import dev.babies.application.docker.AbstractDockerService
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.application.ssl.SslManager
import dev.babies.applicationDirectory
import dev.babies.isDevelopment
import dev.babies.utils.docker.doesContainerExist
import dev.babies.utils.docker.isContainerRunning
import dev.babies.utils.docker.prepareImage
import kotlinx.serialization.encodeToString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.File

class TraefikService : AbstractDockerService(
    containerName = "traefik" + if (isDevelopment) "_dev" else "",
    image = "traefik:v3.5.1"
), KoinComponent {
    private val sslManager by inject<SslManager>()
    private val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))

    private val traefikDirectory = applicationDirectory
        .resolve("traefik")
        .apply { mkdirs() }

    val traefikDynamicConfig = traefikDirectory
        .resolve("dynamic")
        .apply { mkdirs() }

    val traefikStaticConfig = traefikDirectory.resolve("traefik.yaml")

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
        traefikDynamicConfig.mkdirs()

        val exposedHttpPort = ExposedPort.tcp(80)
        val exposedHttpsPort = ExposedPort.tcp(443)

        val portBindings = Ports()
        portBindings.bind(exposedHttpPort, Ports.Binding.bindPort(if (isDevelopment) 180 else 80))
        portBindings.bind(exposedHttpsPort, Ports.Binding.bindPort(if (isDevelopment) 1443 else 443))

        val defaultConfigFile = {}::class.java.classLoader.getResourceAsStream("traefik/traefik.yaml")
            ?.bufferedReader(Charsets.UTF_8)
            ?.readText()!!

        traefikStaticConfig.writeText(defaultConfigFile)

        val staticConfigurationBinding = Bind.parse("${traefikStaticConfig.canonicalPath}:/etc/traefik/traefik.yaml")
        val dynamicConfigurationBinding = Bind.parse("${traefikDynamicConfig.canonicalPath}:/etc/traefik/dynamic/")
        val certificatesBinding = Bind.parse("${sslManager.sslDirectory.canonicalPath}:/certificates:ro")
        val dockerSocketBinding = Bind.parse("/var/run/docker.sock:/var/run/docker.sock")

        dockerClient
            .createContainerCmd(image)
            .withName(containerName)
            .withHostConfig(
                HostConfig()
                    .withPortBindings(portBindings)
                    .withBinds(staticConfigurationBinding, dynamicConfigurationBinding, certificatesBinding, dockerSocketBinding)
                    .withNetworkMode(dockerNetwork.networkName)
            )
            .withLabels(
                mapOf("com.docker.compose.project" to COMPOSE_PROJECT_PREFIX)
            )
            .exec()
    }

    private fun writeDashboardConfig() {
        var dashboardConfigContent = {}::class.java.classLoader.getResourceAsStream("traefik/dashboard.yaml")
            ?.bufferedReader(Charsets.UTF_8)
            ?.readText()

        if (isDevelopment) dashboardConfigContent = dashboardConfigContent?.replace("local.vocus.dev", "local.vocusdev.internal")

        if (dashboardConfigContent != null) {
            val dashboardConfigFile = traefikDynamicConfig.resolve("dashboard.yml")
            dashboardConfigFile.writeText(dashboardConfigContent)
        }
    }

    private fun writeSslConfigs() {
        val config = TraefikTlsConfig(
            tls = TraefikTlsConfig.Tls(
                certificates = sslManager.getCertificates().map { certificateName ->
                    TraefikTlsConfig.Tls.Certificate(
                        certFile = "/certificates/$certificateName/fullchain.pem",
                        keyFile = "/certificates/$certificateName/cert.key"
                    )
                }
            )
        )
        val certificateConfigFile = traefikDynamicConfig.resolve("certificates.yml")
        certificateConfigFile.writeText(Yaml.default.encodeToString(config))
    }

    private fun writeConfigs() {
        writeDashboardConfig()
        writeSslConfigs()
    }

    override suspend fun start() {
        writeConfigs()
        if (dockerClient.isContainerRunning(containerName)) return
        if (getState() != State.Created) throw IllegalStateException()
        dockerClient.startContainerCmd(containerName).exec()
    }

    override suspend fun stop() {
        if (getState() != State.Created) throw IllegalStateException()
        if (!dockerClient.isContainerRunning(containerName)) return
        dockerClient.stopContainerCmd(containerName).exec()
    }

    fun addRouter(
        name: String,
        file: File,
        host: String,
        pathPrefixes: Set<String> = setOf("/"),
        routerDestination: RouterDestination
    ) {
        val content = when (routerDestination) {
            is RouterDestination.HostPort -> {
                {}::class.java.classLoader.getResourceAsStream("traefik/host-port-service.yaml")
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.readText()!!
                    .replace("DESTINATION_HOST", "host.docker.internal")
                    .replace("NAME", name)
                    .replace("HOST", host)
                    .replace("PATHPREFIX", pathPrefixes.joinToString(" || ") { "PathPrefix(`$it`)" })
                    .replace("PORT", routerDestination.port.toString())
            }
            is RouterDestination.ContainerPort -> {
                {}::class.java.classLoader.getResourceAsStream("traefik/host-port-service.yaml")
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.readText()!!
                    .replace("DESTINATION_HOST", routerDestination.containerName)
                    .replace("NAME", name)
                    .replace("HOST", host)
                    .replace("PATHPREFIX", pathPrefixes.joinToString(" || ") { "PathPrefix(`$it`)" })
                    .replace("PORT", routerDestination.port.toString())
            }
        }
        file.writeText(content)
    }
}

sealed class RouterDestination {
    data class HostPort(val port: Int): RouterDestination()
    data class ContainerPort(val containerName: String, val port: Int): RouterDestination()
}