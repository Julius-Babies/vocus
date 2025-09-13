package dev.babies.application.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import dev.babies.utils.docker.isContainerRunning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class AbstractDockerService(
    val containerName: String,
    val image: String
): KoinComponent {
    protected val dockerClient by inject<DockerClient>()
    abstract suspend fun createIfMissing()
    abstract suspend fun start()
    abstract suspend fun stop()

    enum class State {
        Created,
        Invalid,
        Missing
    }

    suspend fun getState(): State {
        withContext(Dispatchers.IO) {
            val databaseContainer = dockerClient
                .listContainersCmd()
                .withShowAll(true)
                .exec()
                .firstOrNull {containerName in it.names.map { name -> name.dropWhile { c -> c == '/' } } } ?: return@withContext State.Missing
            if (databaseContainer.image != image) return@withContext State.Invalid
            if (databaseContainer.labels["com.docker.compose.project"] != COMPOSE_PROJECT_PREFIX) return@withContext State.Invalid
            return@withContext null
        }?.let { return it }
        return State.Created
    }

    suspend fun isRunning(): Boolean {
        return getState() == State.Created && dockerClient.isContainerRunning(containerName)
    }

    suspend fun isCreated(): Boolean {
        return getState() == State.Created
    }

    suspend fun exec(command: String) {
        withContext(Dispatchers.IO) {
            val cmd = dockerClient.execCreateCmd(containerName)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("sh", "-c", command)
                .exec()

            dockerClient
                .execStartCmd(cmd.id)
                .exec(object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(frame: Frame) {
                        val payload = String(frame.payload)
                        println(payload)
                    }
                })
                .awaitCompletion()
        }
    }
}