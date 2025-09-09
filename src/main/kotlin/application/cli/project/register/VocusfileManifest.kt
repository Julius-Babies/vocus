package dev.babies.application.cli.project.register

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File

@Serializable
data class VocusfileManifest(
    @SerialName("name") val name: String,
    @SerialName("infrastructure") val infrastructure: Infrastructure?,
    @SerialName("modules") val modules: Map<String, Module> = emptyMap(),
) {
    companion object Companion {
        fun readFromFile(file: File): VocusfileManifest {
            val content = file.readText()
            return Yaml.default.decodeFromString(content)
        }
    }

    @Serializable
    data class Infrastructure(
        @SerialName("databases") val databases: List<Database>
    ) {
        @Serializable
        data class Database(
            @SerialName("type") val type: Type,
            @SerialName("version") val version: String = "latest",
            @SerialName("databases") val databases: List<String>
        ) {
            @Serializable
            enum class Type {
                @SerialName("postgres") Postgres,
                @SerialName("mongo") Mongo,
                @SerialName("rabbit") Rabbit
            }
        }
    }

    @Serializable
    data class Module(
        @SerialName("routes") val routes: List<Route> = emptyList(),
        @SerialName("docker") val docker: DockerConfig? = null,
        @SerialName("mtls") val mTls: Boolean = false,
    ) {
        @Serializable
        data class Route(
            @SerialName("subdomain") val domain: String? = null,
            @SerialName("path_prefixes") val pathPrefixes: Set<String> = setOf("/"),
            @SerialName("ports") val ports: Ports? = null
        )

        @Serializable
        data class Ports(
            @SerialName("docker") val docker: Int? = null,
            @SerialName("local") val host: Int? = null,
        ) {
            init {
                require(host != null || docker != null) {
                    "At least one port must be set, either local or docker."
                }
            }
        }

        @Serializable
        data class DockerConfig(
            @SerialName("image") val image: String,
            @SerialName("exposed_ports") val exposedPorts: Map<Int, Int> = emptyMap(),
            @SerialName("env") val env: Map<String, String> = emptyMap(),
        )
    }
}