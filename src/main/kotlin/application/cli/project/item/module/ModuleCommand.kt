package dev.babies.application.cli.project.item.module

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.project.ProjectCommandContext
import dev.babies.application.cli.project.item.module.item.ModuleItemCommand
import dev.babies.application.model.Module
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
            print("- ${aqua(module.name)} ")
            when (module.state) {
                Module.State.Off -> println(gray("disabled"))
                Module.State.Docker -> println(green("Docker"))
                Module.State.Local -> println(green("Local"))
            }
        }
    }

    init {
        subcommands(project.modules.map { module ->
            val moduleItemContext = ModuleItemContext(module, projectCommandContext)
            ModuleItemCommand(moduleItemContext)
        })
    }
}

open class ModuleItemContext(
    val module: Module,
    projectCommandContext: ProjectCommandContext
) : ProjectCommandContext(commandContext = projectCommandContext, project = projectCommandContext.project)