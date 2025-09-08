package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

private object GetBinds : KoinComponent {
    val dockerClient by inject<DockerClient>()
}

fun Container.getBinds(): Set<Bind> {
    return GetBinds.dockerClient.inspectContainerCmd(id)
        .exec()
        .hostConfig
        .binds
        .map {
            Bind(
                hostPath = File(it.path),
                containerPath = it.volume.path
            )
        }
        .toSet()
}

data class Bind(
    val hostPath: File,
    val containerPath: String
)