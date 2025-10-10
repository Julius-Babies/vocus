package dev.babies.utils.docker

import com.github.dockerjava.api.model.HostConfig

fun HostConfig.withHostAliases(aliases: Collection<String>) = this
    .withExtraHosts(*aliases.map { "$it:host-gateway" }.toTypedArray())