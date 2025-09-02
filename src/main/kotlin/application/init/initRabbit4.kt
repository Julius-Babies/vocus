package dev.babies.application.init

import dev.babies.application.config.getConfig
import dev.babies.application.database.rabbitmq.r4.Rabbit4
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitRabbit4 : KoinComponent {
    val rabbit4 by inject<Rabbit4>()
}

suspend fun initRabbit4() {
    InitRabbit4.rabbit4.createIfMissing()
    InitRabbit4.rabbit4.start()

    val projectVhosts = getConfig().projects.flatMap { it.infrastructure.databases?.rabbit4?.vhosts.orEmpty() }.toSet()
    val rabbitVhosts = InitRabbit4.rabbit4.getVHosts() - "/"

    val vhostsToCreate = projectVhosts.filter { vhost -> !rabbitVhosts.contains(vhost) }
    vhostsToCreate.forEach { vhost -> InitRabbit4.rabbit4.createVHost(vhost) }

    val vhostsToDelete = rabbitVhosts.filter { vhost -> !projectVhosts.contains(vhost) }
    vhostsToDelete.forEach { vhost -> InitRabbit4.rabbit4.deleteVHost(vhost) }
}