package dev.babies.application.database.postgres

import dev.babies.applicationDirectory

abstract class AbstractPostgresDatabase(
    val containerName: String
) {
    val dataDirectory = applicationDirectory
        .resolve("data").apply { mkdirs() }
        .resolve("databases").apply { mkdirs() }
        .resolve(containerName).apply { mkdirs() }

    abstract suspend fun createIfMissing()
    abstract suspend fun getDatabases(): List<String>
    abstract suspend fun createDatabase(databaseName: String)
    abstract suspend fun deleteDatabase(databaseName: String)
    abstract suspend fun start()
    abstract suspend fun stop()
}