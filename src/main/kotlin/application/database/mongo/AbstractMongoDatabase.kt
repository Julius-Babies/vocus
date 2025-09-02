package dev.babies.application.database.mongo

import dev.babies.application.docker.AbstractDockerService
import dev.babies.applicationDirectory

abstract class AbstractMongoDatabase(
    containerName: String,
    image: String
) : AbstractDockerService(
    containerName,
    image
) {
    val dataDirectory = applicationDirectory
        .resolve("data").apply { mkdirs() }
        .resolve("databases").apply { mkdirs() }
        .resolve("mongo_$containerName").apply { mkdirs() }

    abstract suspend fun getDatabases(): List<String>
    abstract suspend fun createDatabase(databaseName: String)
    abstract suspend fun deleteDatabase(databaseName: String)
}