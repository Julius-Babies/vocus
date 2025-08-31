package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object GetEnvironmentVariables : KoinComponent {
    val dockerClient by inject<DockerClient>()
}

fun Container.getEnvironmentVariables(): Map<String, String> {
    return GetEnvironmentVariables.dockerClient.inspectContainerCmd(id).exec().config.env.orEmpty().associate {
        val (key, value) = it.split("=", limit = 2)
        key to value
    }
}

fun Map<String, String>.matches(other: Map<String, String>): Boolean {
    return this.all { (key, value) -> other[key] == value }
}