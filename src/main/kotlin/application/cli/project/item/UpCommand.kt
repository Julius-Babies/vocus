package dev.babies.application.cli.project.item

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import dev.babies.application.main.start

class UpCommand : SuspendingCliktCommand("up") {
    override suspend fun run() {
        start()
    }
}