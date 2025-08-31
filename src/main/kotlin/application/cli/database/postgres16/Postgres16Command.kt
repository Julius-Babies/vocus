package dev.babies.application.cli.database.postgres16

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.config.getConfig
import org.koin.core.component.KoinComponent

class Postgres16Command: SuspendingCliktCommand("postgres16"), KoinComponent {

    override suspend fun run() {}

    init {
        subcommands(
            getConfig().databases.postgres16?.databases.orEmpty().map { databaseName ->
                PostgresDatabaseCommand(databaseName)
            }
        )
    }
}