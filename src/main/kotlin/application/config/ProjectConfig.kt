package dev.babies.application.config

import dev.babies.application.cli.project.item.module.item.SetStateCommand
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ProjectConfig(
    @SerialName("name") var name: String,
    @SerialName("infrastructure") var infrastructure: Infrastructure = Infrastructure(),
    @SerialName("modules") var modules: Map<String, Module> = mapOf()
) {

    @Transient val projectDomain = DomainBuilder(vocusDomain).addSubdomain(DomainBuilder.nameToDomain(name))

    fun getAllProjectDomains(): Set<String> {
        val domains = mutableSetOf<String>()
        domains.add(projectDomain.toString())
        modules.values.forEach { module ->
            module.routes.forEach forEachRoute@{ route ->
                if (route.subdomain.isNullOrBlank()) return@forEachRoute
                domains.add(DomainBuilder(projectDomain).addSubdomain(route.subdomain).toString())
            }
        }
        return domains
    }

    @Serializable
    data class Infrastructure(
        @SerialName("databases") var databases: Databases? = Databases()
    ) {
        @Serializable
        data class Databases(
            @SerialName("postgres16") var postgres16: Postgres16? = Postgres16()
        ) {
            @Serializable
            data class Postgres16(
                @SerialName("databases") var databases: List<String> = listOf()
            )
        }
    }

    @Serializable
    data class Module(
        @SerialName("docker_config") val dockerConfig: DockerConfig?,
        @SerialName("routes") val routes: List<Route> = emptyList(),
        @SerialName("current_state") val currentState: SetStateCommand.State = SetStateCommand.State.Off
    ) {
        @Serializable
        data class DockerConfig(
            @SerialName("image") val image: String,
            @SerialName("exposed_ports") val exposedPorts: Map<Int, Int> = emptyMap(),
            @SerialName("env") val env: Map<String, String> = emptyMap()
        )

        @Serializable
        data class Route(
            @SerialName("subdomain") val subdomain: String? = null,
            @SerialName("path_prefixes") val pathPrefixes: Set<String> = setOf("/"),
            @SerialName("ports") val ports: Ports
        ) {

            @Serializable
            data class Ports(
                @SerialName("docker_port") val docker: Int?,
                @SerialName("host_port") val host: Int?
            ) {
                init {
                    require(host != null || docker != null) {
                        "At least one port must be set, either host or docker."
                    }
                }
            }
        }
    }
}