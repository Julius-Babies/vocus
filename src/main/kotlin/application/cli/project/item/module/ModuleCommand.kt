package dev.babies.application.cli.project.item.module

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.project.ProjectCommandContext
import dev.babies.application.cli.project.item.module.item.ModuleItemCommand
import dev.babies.application.cli.project.item.module.item.SetStateCommand
import dev.babies.application.config.ProjectConfig
import dev.babies.utils.aqua
import dev.babies.utils.gray
import dev.babies.utils.green
import org.koin.core.component.KoinComponent

class ModuleCommand(
    projectCommandContext: ProjectCommandContext
) : SuspendingCliktCommand("module"), KoinComponent {
    override val invokeWithoutSubcommand: Boolean = true

    private val project = projectCommandContext.project
    override suspend fun run() {
        if (currentContext.invokedSubcommand != null) return
        println("Modules for ${project.name}")
        project.modules.forEach { module ->
            println("- " + aqua(module.key) + " " + (if (module.value.currentState == SetStateCommand.State.Off) gray("disabled") else green(if (module.value.currentState == SetStateCommand.State.Docker) "Docker" else "Local")))
        }
    }

    init {
        subcommands(project.modules.map { (moduleName, module) ->
            val moduleItemContext = ModuleItemContext(moduleName, module, projectCommandContext)
            ModuleItemCommand(moduleItemContext)
        })
    }
}

open class ModuleItemContext(
    val moduleName: String,
    val module: ProjectConfig.Module,
    projectCommandContext: ProjectCommandContext
) : ProjectCommandContext(commandContext = projectCommandContext, project = projectCommandContext.project)