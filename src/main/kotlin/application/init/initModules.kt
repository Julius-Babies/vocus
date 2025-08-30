package dev.babies.application.init

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import dev.babies.application.cli.project.item.module.item.SetStateCommand
import dev.babies.application.config.ProjectConfig
import dev.babies.application.config.getConfig
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain
import dev.babies.application.reverseproxy.RouterDestination
import dev.babies.application.reverseproxy.TraefikService
import dev.babies.utils.docker.getContainerByName
import dev.babies.utils.docker.prepareImage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.File

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
            if (module.currentState != SetStateCommand.State.Docker) {
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
                            mapOf("com.docker.compose.project" to "${COMPOSE_PROJECT_PREFIX}_${project.name}")
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

            fun getTraefikRouterNameFromRoute(route: ProjectConfig.Module.Route): String {
                return project.projectDomain.toString() + "-$moduleName-${route.subdomain}-${route.pathPrefixes.hashCode()}"
            }
            fun getTraefikRouterFileFromRoute(route: ProjectConfig.Module.Route): File {
                val name = getTraefikRouterNameFromRoute(route)
                val routerFile = InitModules.traefikService.traefikDynamicConfig.resolve("$name-module.yaml")
                return routerFile
            }

            if (module.currentState == SetStateCommand.State.Off) {
                module.routes.forEach { route ->
                    val routerFile = getTraefikRouterFileFromRoute(route)
                    if (routerFile.exists()) routerFile.delete()
                }
                return@forEach
            }

            module.routes.forEach forEachRoute@{ route ->
                InitModules.traefikService.addRouter(
                    name = getTraefikRouterNameFromRoute(route),
                    file = getTraefikRouterFileFromRoute(route),
                    host = if (route.subdomain.isNullOrBlank()) {
                        project.projectDomain.buildAsSubdomain(skipIfSuffixAlreadyPresent = true, suffix = vocusDomain)
                    } else {
                        DomainBuilder(project.projectDomain).addSubdomain(route.subdomain).buildAsSubdomain(suffix = vocusDomain)
                    },
                    pathPrefixes = route.pathPrefixes,
                    routerDestination = when (module.currentState) {
                        SetStateCommand.State.Docker -> RouterDestination.ContainerPort(dockerContainerName, route.ports.docker!!)
                        SetStateCommand.State.Local -> RouterDestination.HostPort(route.ports.host!!)
                        SetStateCommand.State.Off -> return@forEachRoute
                    }
                )
            }
        }
    }
}