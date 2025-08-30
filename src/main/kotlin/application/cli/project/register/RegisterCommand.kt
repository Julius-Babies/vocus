package dev.babies.application.cli.project.register

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import dev.babies.application.config.ProjectConfig
import dev.babies.application.config.getConfig
import dev.babies.application.config.updateConfig
import dev.babies.application.init.initHosts
import dev.babies.application.init.initPostgres16
import dev.babies.application.init.initSsl
import dev.babies.application.init.updateDockerNetwork
import dev.babies.utils.aqua
import dev.babies.utils.green
import dev.babies.utils.red
import dev.babies.utils.yellow
import kotlinx.serialization.SerializationException
import java.io.File
import kotlin.io.path.Path

private val validConfigFileNames = setOf("vocusfile", "Vocusfile", "vocusfile.yaml", "Vocusfile.yaml", "vocusfile.yml", "Vocusfile.yml")

class RegisterCommand : SuspendingCliktCommand("register") {
    val vocusfilePath by option(
        "--vocusfile",
        help = "Path to the vocusfile. The default is the vocusfile in the current directory. If specified, its parent will be treated as the project directory."
    ).path(mustExist = true, canBeDir = false).defaultLazy { Path(".") }

    override suspend fun run() {
        val currentDirectory = vocusfilePath.toFile().let {
            if (it.isFile) it.parentFile else it
        }
        val vocusProjectFile = if (vocusfilePath != File(".")) validConfigFileNames.firstNotNullOfOrNull {
            val configFile = currentDirectory.resolve(it)
            if (configFile.exists()) configFile else null
        } else vocusfilePath.toFile()

        if (vocusProjectFile == null) {
            val validFileNames = validConfigFileNames.sorted().joinToString("\n") { "- ${aqua(it)}" }
            println(red("No valid vocusfile found in the current directory."))
            println()
            println("Valid file names are")
            println(validFileNames)
            return
        }

        val config = try {
            VocusfileManifest.readFromFile(vocusProjectFile)
        } catch (e: SerializationException) {
            println(red("Error parsing vocusfile: ${e.message}"))
            return
        }

        updateDockerNetwork()

        println("Setting up project " + green(config.name))

        val applicationConfig = getConfig()
        if (applicationConfig.projects.any { it.name == config.name }) {
            println(yellow("Project ${config.name} already registered. It will be replaced."))
            println()
        }

        updateConfig { applicationConfig ->
            applicationConfig.projects = applicationConfig.projects.filterNot { it.name == config.name.lowercase() }
            applicationConfig.projects += ProjectConfig(
                name = config.name.lowercase(),
                infrastructure = ProjectConfig.Infrastructure(
                    databases = ProjectConfig.Infrastructure.Databases(
                        postgres16 = config.infrastructure?.databases?.firstOrNull { it.type == VocusfileManifest.Infrastructure.Database.Type.Postgres && it.version == "16" }?.let { databaseConfig ->
                            ProjectConfig.Infrastructure.Databases.Postgres16(
                                databases = databaseConfig.databases
                            )
                        }
                    )
                ),
                modules = config.modules.mapValues { (_, module) ->
                    ProjectConfig.Module(
                        dockerConfig = module.docker?.let { dockerConfig ->
                            ProjectConfig.Module.DockerConfig(
                                image = dockerConfig.image.let { image ->
                                    if (image.contains(":")) image else "$image:latest"
                                },
                                exposedPorts = dockerConfig.exposedPorts,
                                env = dockerConfig.env
                            )
                        },
                        routes = module.routes.map { route ->
                            ProjectConfig.Module.Route(
                                subdomain = route.domain,
                                pathPrefixes = route.pathPrefixes.toSet().ifEmpty { setOf("/") },
                                ports = ProjectConfig.Module.Route.Ports(
                                    docker = route.ports?.docker,
                                    host = route.ports?.host
                                )
                            )
                        }
                    )
                }
            )

            applicationConfig
        }

        if (config.infrastructure?.databases?.firstOrNull { it.type == VocusfileManifest.Infrastructure.Database.Type.Postgres && it.version == "16" } != null) initPostgres16()
        initSsl()
        initHosts()
    }
}