package dev.babies.application.init

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import dev.babies.application.cli.project.item.module.item.SetStateCommand
import dev.babies.application.config.getConfig
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.application.reverseproxy.RouterDestination
import dev.babies.application.reverseproxy.TraefikService
import dev.babies.utils.docker.getContainerByName
import dev.babies.utils.docker.prepareImage
import dev.babies.utils.domain.nameToDomain
import dev.babies.utils.domain.withLocalVocusSuffix
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

private object InitModules : KoinComponent {
    val dockerClient by inject<DockerClient>()
    val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
    val traefikService by inject<TraefikService>()
}

fun initModules() {
    val projects = getConfig().projects

    projects.forEach { project ->
        val moduleRouterDirectory = InitModules.traefikService.traefikDynamicConfig.resolve("modules")
        if (moduleRouterDirectory.exists()) moduleRouterDirectory.deleteRecursively()
        moduleRouterDirectory.mkdirs()
        project.modules.forEach { (moduleName, module) ->
            val dockerContainerName = "${project.name}_$moduleName"
            val projectRouterDirectory = moduleRouterDirectory.resolve(project.name).apply { mkdirs() }
            if (module.currentState != SetStateCommand.State.Off) {
                // Delete Docker container if it exists
                val container = InitModules.dockerClient.getContainerByName(dockerContainerName)
                if (container != null) {
                    InitModules.dockerClient
                        .removeContainerCmd(container.id)
                        .withForce(true)
                        .exec()
                }
            }

            if (module.currentState == SetStateCommand.State.Docker) {
                InitModules.dockerClient.prepareImage(module.dockerConfig!!.image)
                val existingContainer = InitModules.dockerClient.getContainerByName(dockerContainerName)
                var recreate = false
                if (existingContainer == null) recreate = true
                else {
                    if (existingContainer.image != module.dockerConfig.image) recreate = true
                }

                if (recreate) {
                    if (existingContainer != null) {
                        InitModules.dockerClient
                            .removeContainerCmd(existingContainer.id)
                            .withForce(true)
                            .exec()
                    }

                    val exposedPorts = module.dockerConfig.exposedPorts.map { port ->
                        ExposedPort.tcp(port)
                    }.toTypedArray()

                    InitModules.dockerClient.createContainerCmd(module.dockerConfig.image)
                        .withName(dockerContainerName)
                        .withLabels(
                            mapOf("com.docker.compose.project" to "vocus_${project.name}")
                        )
                        .withHostConfig(
                            HostConfig()
                                .withAutoRemove(true)
                                .withNetworkMode(InitModules.dockerNetwork.networkName)
                        )
                        .withExposedPorts(*exposedPorts)
                        .exec()

                    InitModules.dockerClient.startContainerCmd(dockerContainerName).exec()
                }

            }
            val moduleDirectory = projectRouterDirectory.resolve(moduleName).apply { mkdirs() }

            module.routes.forEach { route ->
                val name = project.projectDomain + "-$moduleName-${route.subdomain}-${route.pathPrefixes.hashCode()}"
                val routerFile = moduleDirectory.resolve("$name.yaml")

                InitModules.traefikService.addRouter(
                    name = name,
                    file = routerFile,
                    host = if (route.subdomain.isNullOrBlank()) project.projectDomain.withLocalVocusSuffix() else (route.subdomain.nameToDomain() + "." + project.projectDomain).withLocalVocusSuffix(),
                    pathPrefixes = route.pathPrefixes,
                    routerDestination = when (module.currentState) {
                        SetStateCommand.State.Docker -> RouterDestination.ContainerPort(dockerContainerName, route.ports.docker!!)
                        SetStateCommand.State.Local -> RouterDestination.HostPort(route.ports.host!!)
                        else -> throw IllegalStateException()
                    }
                )
            }
        }
    }
}