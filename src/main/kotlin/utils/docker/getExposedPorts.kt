package dev.babies.utils.docker

import com.github.dockerjava.api.model.Container

fun Container.getExposedPorts(): Map<Int, Int> {
    val ports = ports.orEmpty()
        .associate { it.publicPort to it.privatePort }
        .filterKeys { it != null }
        .filterValues { it != null }
    @Suppress("UNCHECKED_CAST")
    return ports as Map<Int, Int>
}