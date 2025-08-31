package dev.babies.utils.docker

import com.github.dockerjava.api.model.Container

fun Container.getExposedPorts(): List<ExposedPort> {
    val ports = ports.orEmpty()
        .mapNotNull {
            ExposedPort(
                hostPort = it.publicPort ?: return@mapNotNull null,
                containerPort = it.privatePort ?: return@mapNotNull null,
                protocol = if (it.type == "tcp") ExposedPort.Protocol.TCP else ExposedPort.Protocol.UDP
            )
        }

    return ports
}

data class ExposedPort(
    val hostPort: Int,
    val containerPort: Int,
    val protocol: Protocol
) {
    enum class Protocol { TCP, UDP }
}

fun List<ExposedPort>.matches(other: List<ExposedPort>): Boolean =
    this.all { it in other } && other.all { it in this }