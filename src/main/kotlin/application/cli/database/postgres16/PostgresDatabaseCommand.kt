package dev.babies.application.cli.database.postgres16

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands

class PostgresDatabaseCommand(databaseName: String) : SuspendingCliktCommand(databaseName) {
    override suspend fun run() {}

    init {
        subcommands(ImportCommand(databaseName))
    }
}