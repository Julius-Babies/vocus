package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container

fun DockerClient.getContainerByName(containerName: String): Container? {
    val containers = listContainersCmd()
        .withShowAll(true)
        .exec()
    return containers.find { it.names.map { name -> name.substringAfter("/") }.contains(containerName) }
}