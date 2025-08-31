package dev.babies.application.cli.boot

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import dev.babies.application.init.initDns
import dev.babies.application.init.initHosts
import dev.babies.application.init.initPgAdmin
import dev.babies.application.init.initPostgres16
import dev.babies.application.init.initSsl
import dev.babies.application.init.initTraefik
import org.koin.core.component.KoinComponent

class BootCommand : SuspendingCliktCommand("boot"), KoinComponent {

    override fun helpEpilog(context: Context): String {
        return "starts all core services required to run vocus"
    }

    override suspend fun run() {
        initHosts()
        initSsl()
        initTraefik()
        initDns()
        initPostgres16()
        initPgAdmin()
    }
}