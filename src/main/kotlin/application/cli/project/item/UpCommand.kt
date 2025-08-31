package dev.babies.application.cli.project.item

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import dev.babies.application.cli.boot.BootCommand
import dev.babies.application.cli.project.ProjectCommandContext

class UpCommand(
    private val projectCommandContext: ProjectCommandContext
) : SuspendingCliktCommand("up") {
    override suspend fun run() {
        BootCommand().run()
        println("Starting project ${projectCommandContext.project.name}")
        projectCommandContext.project.start()
    }
}