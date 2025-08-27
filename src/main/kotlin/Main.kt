package dev.babies

import com.github.ajalt.clikt.command.main
import dev.babies.application.cli.Main
import dev.babies.application.docker.dockerModule
import dev.babies.application.docker.network.dockerNetworkModule
import dev.babies.application.init.initPostgres16
import dev.babies.application.init.updateDockerNetwork
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.io.File

val applicationDirectory = File(".").resolve(".vocusdev").apply { mkdirs() }

fun main(args: Array<String>) {
    startKoin {
        runBlocking {
            modules(dockerModule, dockerNetworkModule)

            updateDockerNetwork()
            initPostgres16()

            Main().main(args)
        }
    }
}