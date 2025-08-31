package dev.babies.application.cli.poweroff

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import dev.babies.application.config.getConfig
import dev.babies.application.database.postgres.p16.Postgres16
import dev.babies.application.database.postgres.pgadmin.Pgadmin
import dev.babies.application.dns.AndroidDnsService
import dev.babies.application.model.Project
import dev.babies.application.reverseproxy.TraefikService
import dev.babies.utils.blue
import dev.babies.utils.green
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PoweroffCommand : SuspendingCliktCommand("poweroff"), KoinComponent {

    private val dnsService by inject<AndroidDnsService>()
    private val pgadmin by inject<Pgadmin>()
    private val postgres16 by inject<Postgres16>()
    private val traefikService by inject<TraefikService>()

    override fun helpEpilog(context: Context): String {
        return "Stop all services including those that are used by vocus."
    }

    override suspend fun run() {
        println(green("Powering off vocus...") + " Please wait")
        println()

        val services = listOf(dnsService, pgadmin, postgres16, traefikService)

        getConfig().projects
            .map { project -> Project.fromConfig(project) }
            .forEach { project -> project.poweroff() }

        services.forEach { service ->
            if (!service.isRunning()) return@forEach
            print("⌛ Stopping " + blue(service.containerName))
            service.stop()
            println("\r✅ Stopped " + blue(service.containerName))
        }

        println()
        println(green("Vocus successfully shut down."))
    }
}