package dev.babies.application.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectConfig(
    @SerialName("name") var name: String,
    @SerialName("infrastructure") var infrastructure: Infrastructure = Infrastructure()
) {

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
}