package dev.babies.application.reverseproxy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TraefikTlsConfig(
    @SerialName("tls") val tls: Tls
) {

    @Serializable
    data class Tls(
        @SerialName("certificates") val certificates: List<Certificate>
    ) {

        @Serializable
        data class Certificate(
            @SerialName("certFile") val certFile: String,
            @SerialName("keyFile") val keyFile: String
        )
    }
}