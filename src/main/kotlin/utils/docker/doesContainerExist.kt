package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient

fun DockerClient.doesContainerExist(containerName: String): Boolean =
    listContainersCmd()
        .withShowAll(true)
        .exec()
        .flatMap { it.names.map { name -> name.substringAfter("/") } }
        .any { it == containerName }