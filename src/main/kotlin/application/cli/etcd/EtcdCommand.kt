package dev.babies.application.cli.etcd

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.babies.application.etcd.EtcdService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EtcdCommand : SuspendingCliktCommand("etcd"), KoinComponent {
    private val etcdService by inject<EtcdService>()
    private val customCommand by option(
        "--custom-command",
        help = "Custom command to execute in etcd container"
    ).flag(default = false)
    private val command by argument().multiple()

    override suspend fun run() {
        etcdService.createIfMissing()
        etcdService.start()
        if (customCommand) etcdService.exec(command.joinToString(" "))
        else etcdService.exec("/etcd/etcdctl ${command.joinToString(" ")}")
    }
}