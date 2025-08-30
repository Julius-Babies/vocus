package dev.babies.application.database.postgres.p16

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import dev.babies.application.database.postgres.AbstractPostgresDatabase
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.isDevelopment
import dev.babies.utils.docker.doesContainerExist
import dev.babies.utils.docker.isContainerRunning
import dev.babies.utils.docker.prepareImage
import dev.babies.utils.docker.runCommand
import dev.babies.utils.waitUntil
import org.koin.core.component.KoinComponent
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.DriverManager

class Postgres16(
    private val dockerNetworkName: String
): AbstractPostgresDatabase(
    containerName = "postgres16" + if (isDevelopment) "_dev" else "",
    image = "postgres:16"
), KoinComponent {

    override suspend fun createIfMissing() {
        val state = getState()
        if (state == State.Created) return
        if (state == State.Invalid) {
            println("The database differs from its required configuration. We'll attempt to remove it and create a new one.")
            println("If this fails, remove all containers that are using the $dockerNetworkName network and try again.")
            dockerClient.removeContainerCmd(containerName).withForce(true).exec()
            println("Database $containerName removed.")
        }
        if (state == State.Missing) {
            println("Generating database $containerName with network $dockerNetworkName")
        }

        dockerClient.prepareImage(image)
        if (dockerClient.doesContainerExist(containerName)) dockerClient.removeContainerCmd(containerName).withForce(true).exec()

        dataDirectory.deleteRecursively()

        val exposedPort = ExposedPort.tcp(5432)
        val portBindings = Ports()
        portBindings.bind(exposedPort, Ports.Binding.bindPort(if (isDevelopment) 15432 else 5432))

        val bind = Bind.parse("${dataDirectory.absolutePath}:/var/lib/postgresql/data")

        dockerClient
            .createContainerCmd(image)
            .withName(containerName)
            .withEnv(
                "POSTGRES_PASSWORD=vocus",
                "POSTGRES_USER=vocusdev"
            )
            .withHostConfig(
                HostConfig()
                    .withPortBindings(portBindings)
                    .withBinds(bind)
                    .withNetworkMode(dockerNetworkName)
            )
            .withExposedPorts(exposedPort)
            .withLabels(
                mapOf("com.docker.compose.project" to COMPOSE_PROJECT_PREFIX)
            )
            .exec()

    }

    override suspend fun getDatabases(): List<String> {
        val databases = mutableListOf<String>()
        with(getConnection()) {
            val statement = createStatement()
            val resultSet = statement.executeQuery("SELECT datname FROM pg_database WHERE datistemplate = false")
            while (resultSet.next()) {
                databases.add(resultSet.getString(1))
            }
            resultSet.close()
            statement.close()
            close()
        }

        return databases
    }

    override suspend fun createDatabase(databaseName: String) {
        getConnection().use {
            // Check if the database exists
            val rs = it.createStatement().executeQuery("SELECT 1 FROM pg_database WHERE datname = '$databaseName'")
            if (rs.next()) return
            rs.close()

            val statement = it.createStatement()
            statement.execute("CREATE DATABASE $databaseName")
            statement.close()
        }
    }

    override suspend fun deleteDatabase(databaseName: String) {
        getConnection().use {
            val statement = it.createStatement()
            statement.execute("DROP DATABASE $databaseName")
            statement.close()
        }
    }

    override suspend fun start() {
        if (dockerClient.isContainerRunning(containerName)) return
        if (getState() != State.Created) throw IllegalStateException()
        dockerClient.startContainerCmd(containerName).exec()

        waitUntil("Postgres $containerName to start") {
            dockerClient.isContainerRunning(containerName)
        }

        waitUntil("Postgres $containerName is ready") {
            dockerClient.runCommand(containerName, listOf("pg_isready", "-U", "vocusdev")) == 0
        }
    }

    override suspend fun stop() {
        if (getState() != State.Created) throw IllegalStateException()
        if (!dockerClient.isContainerRunning(containerName)) return
        dockerClient.stopContainerCmd(containerName).exec()
    }

    private suspend fun getConnection(): Connection {
        var connection: Connection? = null
        waitUntil("Connection ready") {
            try {
                connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/", "vocusdev", "vocus")
                true
            } catch (_: PSQLException) {
                false
            }
        }
        if (connection == null) throw IllegalStateException("Could not connect to database")
        return connection
    }
}