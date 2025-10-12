package dev.babies.application.docker.network

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DockerNetwork(
    private val dockerClient: DockerClient,
    val networkName: String
) {
    val networkAddress = "172.${(networkName.hashCode() and 0xFF)}.${(networkName.reversed().hashCode() and 0xFF)}.0/24"

    suspend fun createIfMissing() {
        val state = getState()
        if (state == State.Created) return
        if (state == State.Invalid) {
            println("The network differs from its required configuration. We'll attempt to remove it and create a new one.")
            println("If this fails, remove all containers that are using the $networkName network and try again.")
            dockerClient.removeNetworkCmd(networkName).exec()
            println("Network $networkName removed.")
        }
        if (state == State.Missing) {
            println("Generating network $networkName with network address $networkAddress")
        }

        dockerClient
            .createNetworkCmd()
            .withName(networkName)
            .withDriver("bridge")
            .withAttachable(true)
            .withIpam(
                Network.Ipam()
                    .withConfig(
                        listOf(
                            Network.Ipam.Config()
                                .withSubnet(networkAddress)
                        )
                    )
            )
            .exec()

        println("Network $networkName created.")
    }

    suspend fun getState(): State {
        return withContext(Dispatchers.IO) {
            val dockerNetwork = dockerClient
                .listNetworksCmd()
                .exec()
                .firstOrNull { it.name == networkName } ?: return@withContext State.Missing
            if (!dockerNetwork.isAttachable) return@withContext State.Invalid
            if (dockerNetwork.driver != "bridge") return@withContext State.Invalid
            if (dockerNetwork.ipam.config.none { it.subnet == networkAddress }) return@withContext State.Invalid
            return@withContext State.Created
        }
    }

    enum class State {
        Created,
        Invalid,
        Missing
    }
}