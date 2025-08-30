package dev.babies.application.cli.project.item.module.item

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.enum
import dev.babies.application.cli.project.item.module.ModuleItemContext
import dev.babies.application.config.updateConfig
import dev.babies.application.init.initModules
import dev.babies.application.init.initTraefik
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent

class SetStateCommand(
    private val moduleItemContext: ModuleItemContext
) : SuspendingCliktCommand("set-state"), KoinComponent {

    val target by argument(
        "TARGET",
        help = "The target module which contains the item state"
    ).enum<State>()

    @Serializable
    enum class State {
        @SerialName("off") Off,
        @SerialName("docker") Docker,
        @SerialName("local") Local
    }

    override suspend fun run() {
        println("Set ${moduleItemContext.moduleName} state to $target")

        updateConfig { config ->
            val currentProject = moduleItemContext.project
            currentProject.modules = currentProject.modules.toMutableMap().apply {
                this[moduleItemContext.moduleName] = moduleItemContext.module.copy(
                    currentState = target
                )
            }

            config.projects = config.projects.filter { it.name != moduleItemContext.project.name } + currentProject

            config
        }

        initTraefik()
        initModules()
    }
}