package dev.babies.application.database.rabbitmq

import dev.babies.application.docker.AbstractDockerService
import dev.babies.applicationDirectory

abstract class AbstractRabbitInstance(
    containerName: String,
    image: String
) : AbstractDockerService(
    containerName,
    image
) {
    val dataDirectory = applicationDirectory
        .resolve("data").apply { mkdirs() }
        .resolve("databases").apply { mkdirs() }
        .resolve("rabbit_$containerName").apply { mkdirs() }

    abstract suspend fun getVHosts(): List<String>
    abstract suspend fun createVHost(vHostName: String)
    abstract suspend fun deleteVHost(vHostName: String)
}