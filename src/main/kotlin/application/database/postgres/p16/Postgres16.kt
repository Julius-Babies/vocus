package dev.babies.application.database.postgres.p16

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import dev.babies.application.config.ApplicationConfig
import dev.babies.application.config.updateConfig
import dev.babies.application.database.postgres.AbstractPostgresDatabase
import dev.babies.application.docker.COMPOSE_PROJECT_PREFIX
import dev.babies.isDevelopment
import dev.babies.utils.docker.*
import dev.babies.utils.waitUntil
import org.koin.core.component.KoinComponent
import org.postgresql.util.PSQLException
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

private const val DATABASE_USER = "vocusdev"

class Postgres16(
    private val dockerNetworkName: String
): AbstractPostgresDatabase(
    containerName = "postgres16" + if (isDevelopment) "_dev" else "",
    image = "postgres:16"
), KoinComponent {

    private val postgresPort = if (isDevelopment) 15432 else 5432

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
        portBindings.bind(exposedPort, Ports.Binding.bindPort(postgresPort))

        val bind = Bind.parse("${dataDirectory.canonicalPath}:/var/lib/postgresql/data")

        dockerClient
            .createContainerCmd(image)
            .withName(containerName)
            .withEnv(
                "POSTGRES_PASSWORD=vocus",
                "POSTGRES_USER=$DATABASE_USER"
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
            if (rs.next()) return@use
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
            dockerClient.runCommand(containerName, listOf("pg_isready", "-U", DATABASE_USER)).exitCode == 0
        }

        val databases = getDatabases() - "postgres"
        updateConfig { config ->
            config.databases.postgres16 = (config.databases.postgres16 ?: ApplicationConfig.Database.Postgres16()).apply {
                this.databases = databases
            }

            config
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
                connection = DriverManager.getConnection("jdbc:postgresql://localhost:$postgresPort/", DATABASE_USER, "vocus")
                true
            } catch (_: PSQLException) {
                false
            }
        }
        if (connection == null) throw IllegalStateException("Could not connect to database")
        return connection
    }

    suspend fun importDatabase(dumpFile: File, database: String) {
        val wasRunningOnStart = isRunning()
        this.start()
        val containerId = dockerClient.getContainerByName(containerName)!!.id

        dockerClient.runCommand(containerId, listOf("rm", "-f", "/${dumpFile.name}"))

        dockerClient.copyArchiveToContainerCmd(containerId)
            .withHostResource(dumpFile.canonicalPath)
            .withRemotePath("/")
            .exec()

        dockerClient.runCommand(containerId, listOf("dropdb", "-U", DATABASE_USER, "--force", "--if-exists", database))
        dockerClient.runCommand(containerId, listOf("createdb", "-U", DATABASE_USER, database))
        dockerClient.runCommand(containerId, listOf("psql", "-U", DATABASE_USER, "-d", database, "-f", "/${dumpFile.name}"))

        if (!wasRunningOnStart) this.stop()
    }
}