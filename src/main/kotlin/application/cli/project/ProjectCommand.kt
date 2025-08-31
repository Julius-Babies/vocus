package dev.babies.application.cli.project

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.CommandContext
import dev.babies.application.cli.project.item.ProjectItemCommand
import dev.babies.application.cli.project.register.RegisterCommand
import dev.babies.application.config.getConfig
import dev.babies.application.model.Project
import dev.babies.utils.blue
import dev.babies.utils.gray

class ProjectCommand(
    private val commandContext: CommandContext
) : SuspendingCliktCommand("project") {
    override val invokeWithoutSubcommand: Boolean = true

    override suspend fun run() {
        if (currentContext.invokedSubcommand != null) return
        println("Vocus projects")

        val projects = getConfig().projects.sortedBy { it.name }
        projects.forEach { project ->
            println("- ${blue(project.name)}")
        }

        if (projects.isEmpty()) println(gray("No projects registered"))
    }

    init {
        val projects = getConfig().projects
        subcommands(
            *projects.map { ProjectItemCommand(ProjectCommandContext(
                commandContext = commandContext,
                project = Project.fromConfig(it)
            )) }.toTypedArray(),
            RegisterCommand(),
        )
    }
}

open class ProjectCommandContext(
    commandContext: CommandContext,
    val project: Project
) : CommandContext(
    executionDirectory = commandContext.executionDirectory,
)