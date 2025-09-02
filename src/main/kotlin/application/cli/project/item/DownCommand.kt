package dev.babies.application.cli.project.item

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import dev.babies.application.cli.project.ProjectCommandContext
import org.koin.core.component.KoinComponent

class DownCommand(
    private val proccionContext: ProjectCommandContext
) : SuspendingCliktCommand("down"), KoinComponent {
    override suspend fun run() {
        println("Stopping project ${proccionContext.project.name}")
        proccionContext.project.stop()
    }
}