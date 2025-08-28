package dev.babies.application.cli.project.item

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands

class ProjectItemCommand(projectName: String) : SuspendingCliktCommand(projectName) {
    override suspend fun run() {}

    init {
        subcommands(UpCommand())
    }
}