package dev.babies.application.docker

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import dev.babies.isDevelopment
import org.koin.dsl.module

val COMPOSE_PROJECT_PREFIX = if (isDevelopment) "vocus_dev" else "vocus"

val dockerModule = module {
    single {
        DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("unix:///var/run/docker.sock")
            .withDockerTlsVerify(false)
            .build()
            .let { config ->
                val httpClient = ZerodepDockerHttpClient.Builder()
                    .dockerHost(config.dockerHost)
                    .sslConfig(config.sslConfig)
                    .build()
                DockerClientImpl.getInstance(config, httpClient)
            }
    }
}