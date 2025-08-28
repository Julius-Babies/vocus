package dev.babies

import com.github.ajalt.clikt.command.main
import dev.babies.application.cli.Main
import dev.babies.application.database.postgres.p16.postgres16Module
import dev.babies.application.docker.dockerModule
import dev.babies.application.docker.network.dockerNetworkModule
import dev.babies.application.init.initHosts
import dev.babies.application.init.initPostgres16
import dev.babies.application.init.initSsl
import dev.babies.application.init.initTraefik
import dev.babies.application.init.updateDockerNetwork
import dev.babies.application.os.host.hostsManagerModule
import dev.babies.application.os.sudoManagerModule
import dev.babies.application.reverseproxy.traefikModule
import dev.babies.application.ssl.sslModule
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.io.File

val applicationDirectory = File(".").resolve(".vocusdev").apply { mkdirs() }

fun main(args: Array<String>) {
    startKoin {
        runBlocking {
            modules(
                dockerModule,
                dockerNetworkModule,
                sslModule,
                sudoManagerModule,
                hostsManagerModule,
                traefikModule,
                postgres16Module
            )

            updateDockerNetwork()
            initPostgres16()
            initSsl()
            initHosts()
            initTraefik()

            Main().main(args)
        }
    }
}