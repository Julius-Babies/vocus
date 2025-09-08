package dev.babies.application.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import dev.babies.isDevelopment
import dev.babies.utils.yellow
import org.koin.dsl.module
import java.net.SocketException

val COMPOSE_PROJECT_PREFIX = if (isDevelopment) "vocus_dev" else "vocus"

val dockerModule = module {
    single {
        try {
            getDockerClient("unix:///var/run/docker.sock")
        } catch (e: RuntimeException) {
            if (e.cause !is SocketException) throw e
            val userSocket = "unix://${System.getProperty("user.home")}/.docker/run/docker.sock"
            println(yellow("Could not connect to docker socket. Using user socket at $userSocket as fallback."))
            getDockerClient(userSocket)
        }
    }
}

private fun getDockerClient(socket: String): DockerClient {
    return DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(socket)
        .withDockerTlsVerify(false)
        .build()
        .let { config ->
            val httpClient = ZerodepDockerHttpClient.Builder()
                .dockerHost(config.dockerHost)
                .sslConfig(config.sslConfig)
                .build()
            DockerClientImpl.getInstance(config, httpClient)
        }
        .also { it.listContainersCmd().exec() /* Test connection */ }
}