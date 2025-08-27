package dev.babies

import com.github.ajalt.clikt.command.main
import dev.babies.application.cli.Main
import dev.babies.application.docker.dockerModule
import dev.babies.application.docker.network.dockerNetworkModule
import dev.babies.application.init.initPostgres16
import dev.babies.application.init.initSsl
import dev.babies.application.init.updateDockerNetwork
import dev.babies.application.ssl.sslModule
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.io.File

val applicationDirectory = File(".").resolve(".vocusdev").apply { mkdirs() }

fun main(args: Array<String>) {
    startKoin {
        runBlocking {
            modules(dockerModule, dockerNetworkModule, sslModule)

            updateDockerNetwork()
            initPostgres16()
            initSsl()

            Main().main(args)
        }
    }
}