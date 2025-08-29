package dev.babies.application.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.project.ProjectCommand
import java.io.File

class Main : SuspendingCliktCommand("localenv") {
    override suspend fun run() {}


    init {
        val context = CommandContext(
            executionDirectory = File("."),
        )

        subcommands(ProjectCommand(context))
    }
}

open class CommandContext(
    val executionDirectory: File
)