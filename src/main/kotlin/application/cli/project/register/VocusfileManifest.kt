package dev.babies.application.cli.project.register

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File

@Serializable
internal data class VocusfileManifest(
    @SerialName("name") val name: String,
    @SerialName("hosts") val hosts: List<String> = emptyList(),
    @SerialName("infrastructure") val infrastructure: Infrastructure?
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
                @SerialName("postgres") Postgres
            }
        }
    }
}