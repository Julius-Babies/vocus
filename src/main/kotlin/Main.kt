package dev.babies

import dev.babies.application.docker.dockerModule
import dev.babies.application.docker.network.dockerNetworkModule
import dev.babies.application.init.initPostgres16
import dev.babies.application.init.updateDockerNetwork
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.io.File

val applicationDirectory = File(System.getProperty("user.home")).resolve(".vocusdev").apply { mkdirs() }

fun main() {
    startKoin {
        runBlocking {
            modules(dockerModule, dockerNetworkModule)

            updateDockerNetwork()
            initPostgres16()
        }
    }
}