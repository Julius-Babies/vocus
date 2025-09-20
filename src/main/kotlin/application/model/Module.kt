package dev.babies.application.model

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import dev.babies.application.cli.project.item.module.item.SetStateCommand
import dev.babies.application.config.ProjectConfig
import dev.babies.application.config.updateConfig
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain
import dev.babies.application.reverseproxy.RouterDestination
import dev.babies.application.reverseproxy.TraefikService
import dev.babies.application.ssl.SslManager
import dev.babies.utils.REPLACE_LINE
import dev.babies.utils.blue
import dev.babies.utils.docker.getBinds
import dev.babies.utils.docker.getContainerByName
import dev.babies.utils.docker.getEnvironmentVariables
import dev.babies.utils.docker.getExposedPorts
import dev.babies.utils.docker.matches
import dev.babies.utils.docker.prepareImage
import dev.babies.utils.red
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.File
import kotlin.system.exitProcess

class Module(
    val name: String,
    val project: Project,
    val useMtls: Boolean,
    val dockerConfiguration: DockerConfiguration?,
    val routes: List<ProjectConfig.Module.Route>,
    private var _state: State
): KoinComponent {

    private val sslManager by inject<SslManager>()

    val state: State
        get() = _state
    suspend fun setState(to: State) {
        if (this._state == to) return
        this._state = to

        updateConfig { config ->
            val configProject = config.projects.first { it.name == project.name }
            configProject.modules = configProject.modules.toMutableMap().apply {
                this[name] = this[name]?.copy(
                    currentState = when(to) {
                        State.Off -> SetStateCommand.State.Off
                        State.Docker -> SetStateCommand.State.Docker
                        State.Local -> SetStateCommand.State.Local
                    }
                ) ?: error("Module $name not found in project ${project.name}")
            }

            config.projects = config.projects.filterNot { it.name == project.name } + configProject

            config
        }

        start()
    }

    private val dockerClient by inject<DockerClient>()
    private val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
    private val traefikService by inject<TraefikService>()

    val dockerContainerName: String
        get() = "${project.name}_$name"

    companion object {
        fun fromConfig(project: Project, name: String, module: ProjectConfig.Module): Module {
            return Module(
                name = name,
                project = project,
                dockerConfiguration = module.dockerConfig?.let { moduleDockerConfig ->
                    DockerConfiguration(
                        image = moduleDockerConfig.image,
                        exposedPorts = moduleDockerConfig.exposedPorts.map { (host, container) ->
                            dev.babies.utils.docker.ExposedPort(
                                hostPort = host,
                                containerPort = container,
                                protocol = dev.babies.utils.docker.ExposedPort.Protocol.TCP
                            )
                        },
                        env = moduleDockerConfig.env,
                    )
                },
                useMtls = module.mTls,
                routes = module.routes,
                _state = when (module.currentState) {
                    SetStateCommand.State.Off -> State.Off
                    SetStateCommand.State.Docker -> State.Docker
                    SetStateCommand.State.Local -> State.Local
                }
            )
        }
    }

    fun poweroff() {
        if (dockerConfiguration == null) return
        val container = getDockerContainer() ?: return
        print("⌛ Stopping " + blue(project.name + "/" + name))
        dockerClient
            .removeContainerCmd(container.id)
            .withForce(true)
            .exec()
        println("$REPLACE_LINE✓ Stopped " + blue(project.name + "/" + name))
    }

    fun start() {
        if (_state != State.Docker) {
            poweroff()
            getDockerContainer()?.let {
                dockerClient.removeContainerCmd(it.id).withForce(true).exec()
            }
        }

        if (_state == State.Docker) {
            requireNotNull(dockerConfiguration) { "Docker configuration is required for Docker modules" }
            dockerClient.prepareImage(dockerConfiguration.image)
            val existingContainer = getDockerContainer()
            var recreate = false

            val desiredBinds = setOfNotNull(
                if (this.useMtls) dev.babies.utils.docker.Bind(
                    hostPath = sslManager.sslDirectory.resolve("root-ca.crt").canonicalFile,
                    containerPath = "/mTLS/root-ca.crt"
                ) else null,
                if (this.useMtls) dev.babies.utils.docker.Bind(
                    hostPath = sslManager.sslDirectory.resolve("service").resolve("${DomainBuilder.nameToDomain(project.name)}.${DomainBuilder.nameToDomain(this.name)}").resolve("bundle.p12").canonicalFile,
                    containerPath = "/mTLS/service_client.p12"
                ) else null,
                if (this.useMtls) dev.babies.utils.docker.Bind(
                    hostPath = sslManager.sslDirectory.resolve(project.projectDomain).resolve("bundle.p12").canonicalFile,
                    containerPath = "/mTLS/service_host.p12"
                ) else null
            )

            if (existingContainer == null) recreate = true
            else dockerConfiguration.let {
                recreate = true
                if (existingContainer.image != dockerConfiguration.image) return@let
                if (!existingContainer.getExposedPorts().matches(dockerConfiguration.exposedPorts)) return@let
                if (!dockerConfiguration.env.matches(existingContainer.getEnvironmentVariables())) return@let
                if (existingContainer.getBinds() != desiredBinds) return@let
                recreate = false
            }

            if (recreate) {
                val consoleContainerName = blue(project.name + "/" + name)
                print("\uD83D\uDC33 Creating new Docker container for module $consoleContainerName")
                if (existingContainer != null) {
                    dockerClient
                        .removeContainerCmd(existingContainer.id)
                        .withForce(true)
                        .exec()
                }

                val exposedPorts = dockerConfiguration.exposedPorts.map { exposed ->
                    when (exposed.protocol) {
                        dev.babies.utils.docker.ExposedPort.Protocol.UDP -> ExposedPort.udp(exposed.containerPort)
                        dev.babies.utils.docker.ExposedPort.Protocol.TCP -> ExposedPort.tcp(exposed.containerPort)
                    }
                }.toTypedArray()

                val ports = Ports()
                dockerConfiguration.exposedPorts.forEach { exposed ->
                    ports.bind(exposedPorts.first { it.port == exposed.containerPort }, Ports.Binding.bindPort(exposed.hostPort))
                }

                val binds = Binds(*desiredBinds.map {
                    Bind.parse("${it.hostPath.canonicalPath}:${it.containerPath}")
                }.toTypedArray())

                dockerClient.createContainerCmd(dockerConfiguration.image)
                    .withName(dockerContainerName)
                    .withLabels(
                        mapOf("com.docker.compose.project" to "${COMPOSE_PROJECT_PREFIX}_${project.name}")
                    )
                    .withHostConfig(
                        HostConfig()
                            .withNetworkMode(dockerNetwork.networkName)
                            .withPortBindings(ports)
                            .withBinds(binds)
                    )
                    .withEnv(dockerConfiguration.env.map { (key, value) -> "$key=$value" })
                    .withExposedPorts(*exposedPorts)
                    .exec()

                print("\r⧖ Starting new Docker container for module $consoleContainerName")
                dockerClient.startContainerCmd(dockerContainerName).exec()
                println("$REPLACE_LINE✓ Started new Docker container for module $consoleContainerName")
            }
        }

        fun getTraefikRouterNameFromRoute(route: ProjectConfig.Module.Route): String {
            return project.projectDomain + "-$name-${route.subdomain}-${route.pathPrefixes.hashCode()}"
        }
        fun getTraefikRouterFileFromRoute(route: ProjectConfig.Module.Route): File {
            val name = getTraefikRouterNameFromRoute(route)
            val routerFile = traefikService.traefikDynamicConfig.resolve("$name-module.yaml")
            return routerFile
        }

        if (_state == State.Off) {
            routes.forEach { route ->
                val routerFile = getTraefikRouterFileFromRoute(route)
                if (routerFile.exists()) routerFile.delete()
            }
            return
        }

        routes.forEach forEachRoute@{ route ->
            traefikService.addRouter(
                name = getTraefikRouterNameFromRoute(route),
                file = getTraefikRouterFileFromRoute(route),
                host = if (route.subdomain.isNullOrBlank()) {
                    project.projectDomain
                } else {
                    DomainBuilder(project.projectDomain).addSubdomain(route.subdomain).buildAsSubdomain(suffix = vocusDomain)
                },
                pathPrefixes = route.pathPrefixes,
                routerDestination = when (_state) {
                    State.Docker -> {
                        if (route.ports.docker == null) {
                            println(red("Fatal: Missing docker port for route ${route.subdomain.let { 
                                DomainBuilder(vocusDomain).apply { 
                                    if (it != null) addSubdomain(it)
                                }.toString()
                            }} (module ${this.project.name}/${this.name})"))
                            exitProcess(1)
                        }
                        RouterDestination.ContainerPort(dockerContainerName, route.ports.docker, this.useMtls)
                    }
                    State.Local -> RouterDestination.HostPort(route.ports.host!!, this.useMtls)
                    State.Off -> return@forEachRoute
                }
            )
        }
    }

    fun getDockerContainer(): Container? {
        if (dockerConfiguration == null) return null
        return dockerClient.getContainerByName(containerName = dockerContainerName)
    }

    class DockerConfiguration(
        val image: String,
        val exposedPorts: List<dev.babies.utils.docker.ExposedPort>,
        val env: Map<String, String>,
    )

    enum class State {
        Docker, Local, Off
    }
}