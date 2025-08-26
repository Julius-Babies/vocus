package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient

fun DockerClient.isContainerRunning(containerName: String): Boolean =
    listContainersCmd()
        .withShowAll(true)
        .exec()
        .filter { it.names.map { name -> name.substringAfter("/") }.contains(containerName) }
        .any { it.state == "running" }