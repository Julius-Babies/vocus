package dev.babies.application.cli.project.item

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.project.ProjectCommandContext
import dev.babies.application.cli.project.item.module.ModuleCommand

class ProjectItemCommand(
    commandContext: ProjectCommandContext,
) : SuspendingCliktCommand(commandContext.project.name) {
    override suspend fun run() {}

    init {
        subcommands(
            UpCommand(commandContext),
            ModuleCommand(commandContext)
        )
    }
}