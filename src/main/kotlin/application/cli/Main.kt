package dev.babies.application.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.project.ProjectCommand

class Main : SuspendingCliktCommand("localenv") {
    override suspend fun run() {}


    init {
        subcommands(ProjectCommand())
    }
}