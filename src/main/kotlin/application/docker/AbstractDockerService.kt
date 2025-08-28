package dev.babies.application.docker

abstract class AbstractDockerService(
    val containerName: String
) {
    abstract suspend fun createIfMissing()
    abstract suspend fun start()
    abstract suspend fun stop()

    enum class State {
        Created,
        Invalid,
        Missing
    }
}