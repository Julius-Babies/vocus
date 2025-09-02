package dev.babies.application.database.mongo.m8

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import com.mongodb.kotlin.client.coroutine.MongoClient
import dev.babies.application.database.mongo.AbstractMongoDatabase
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.application.docker.network.DockerNetwork
import dev.babies.application.docker.network.VOCUS_DOCKER_NETWORK_DI_KEY
import dev.babies.isDevelopment
import dev.babies.utils.docker.doesContainerExist
import dev.babies.utils.docker.isContainerRunning
import dev.babies.utils.docker.prepareImage
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class Mongo8Database: AbstractMongoDatabase(
    containerName = "mongo8" + if (isDevelopment) "_dev" else "",
    image = "mongo:8.0.13-noble"
), KoinComponent {

    private val dockerNetwork by inject<DockerNetwork>(named(VOCUS_DOCKER_NETWORK_DI_KEY))
    private val mongoDbPort = if (isDevelopment) 27018 else 27017

    private val mongoConnectionUrl = "mongodb://vocusdev:vocus@localhost:$mongoDbPort"

    override suspend fun createIfMissing() {
        val state = getState()
        if (state == State.Created) return
        if (state == State.Invalid) {
            println("The MongoDB container does exist but differs from its required configuration. We'll attempt to remove it and create a new one.")
            println("If this fails, remove all containers that are using the $containerName network and try again.")
            dockerClient.removeContainerCmd(containerName).withForce(true).exec()
            println("MongoDB $containerName removed.")
        }
        if (state == State.Missing) {
            println("Generating MongoDB 8 $containerName")
        }

        dockerClient.prepareImage(image)
        if (dockerClient.doesContainerExist(containerName)) dockerClient.removeContainerCmd(containerName).withForce(true).exec()

        dataDirectory.deleteRecursively()
        dataDirectory.mkdirs()

        val exposedPort = ExposedPort.tcp(27017)
        val portBindings = Ports()
        portBindings.bind(exposedPort, Ports.Binding.bindPort(mongoDbPort))

        val bind = Bind.parse("${dataDirectory.absolutePath}:/data/db")

        dockerClient
            .createContainerCmd(image)
            .withName(containerName)
            .withEnv(
                "MONGO_INITDB_ROOT_USERNAME=vocusdev",
                "MONGO_INITDB_ROOT_PASSWORD=vocus"
            )
            .withHostConfig(
                HostConfig()
                    .withPortBindings(portBindings)
                    .withBinds(bind)
                    .withNetworkMode(dockerNetwork.networkName)
            )
            .withExposedPorts(exposedPort)
            .withLabels(
                mapOf("com.docker.compose.project" to COMPOSE_PROJECT_PREFIX)
            )
            .exec()
    }

    private fun getConnection(): MongoClient {
        val client = MongoClient.create(mongoConnectionUrl)
        return client
    }

    override suspend fun getDatabases(): List<String> {
        getConnection().use { client ->
            return client.listDatabases().toList().map { document -> document.getString("name") }
        }
    }

    override suspend fun createDatabase(databaseName: String) {
        getConnection().use { client ->
            client.getDatabase(databaseName).runCommand(
                Document("createUser", "vocusdev")
                    .append("pwd", "vocus")
                    .append("roles", listOf(
                        Document("role", "root")
                            .append("db", "admin"),
                    ))
            )
        }
    }

    override suspend fun deleteDatabase(databaseName: String) {
        getConnection().use { client ->
            client.getDatabase(databaseName).drop()
        }
    }

    override suspend fun start() {
        if (getState() != State.Created) throw IllegalStateException()
        if (!dockerClient.isContainerRunning(containerName))
        dockerClient.startContainerCmd(containerName).exec()
    }

    override suspend fun stop() {
        if (getState() != State.Created) throw IllegalStateException()
        if (!dockerClient.isContainerRunning(containerName)) return
        dockerClient.stopContainerCmd(containerName).exec()
    }
}