package dev.babies.application.cli.project

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.project.item.ProjectItemCommand
import dev.babies.application.cli.project.register.RegisterCommand
import dev.babies.application.config.getConfig
import dev.babies.utils.blue
import dev.babies.utils.gray

class ProjectCommand : SuspendingCliktCommand("project") {
    override val invokeWithoutSubcommand: Boolean = true

    override suspend fun run() {
        if (currentContext.invokedSubcommand != null) return
        println("LocalEnv projects")

        val projects = getConfig().projects.sortedBy { it.name }
        projects.forEach { project ->
            println("- ${blue(project.name)}")
        }

        if (projects.isEmpty()) println(gray("No projects registered"))
    }

    init {
        val projects = getConfig().projects.map { it.name }
        subcommands(
            RegisterCommand(),
            *projects.map { ProjectItemCommand(it) }.toTypedArray()
        )
    }
}