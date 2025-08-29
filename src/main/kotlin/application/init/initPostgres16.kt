package dev.babies.application.init

import dev.babies.application.config.getConfig
import dev.babies.application.database.postgres.p16.Postgres16
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitPostgres16 : KoinComponent {
    val postgres16 by inject<Postgres16>()
}

suspend fun initPostgres16() {
    InitPostgres16.postgres16.createIfMissing()

    val projectDatabases =
        getConfig().projects.flatMap { projectConfig -> projectConfig.infrastructure.databases?.postgres16?.databases.orEmpty() }

    InitPostgres16.postgres16.start()

    val existingDatabases = InitPostgres16.postgres16.getDatabases() - "postgres" - "vocusdev"

    val databasesToCreate = projectDatabases.filter { database -> !existingDatabases.contains(database) }
    val databasesToDelete = existingDatabases.filter { database -> !projectDatabases.contains(database) }

    if (databasesToDelete.isNotEmpty() || databasesToCreate.isNotEmpty()) {
        databasesToCreate.forEach { database -> InitPostgres16.postgres16.createDatabase(database) }
        databasesToDelete.forEach { database -> InitPostgres16.postgres16.deleteDatabase(database) }
    }
}
