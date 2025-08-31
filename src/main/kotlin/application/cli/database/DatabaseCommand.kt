package dev.babies.application.cli.database

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.database.postgres16.Postgres16Command

class DatabaseCommand : SuspendingCliktCommand("database") {
    override suspend fun run() {}

    init {
        subcommands(
            Postgres16Command()
        )
    }
}