package dev.babies.application.init

import dev.babies.application.config.getConfig
import dev.babies.application.database.mongo.m8.Mongo8Database
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitMongo8: KoinComponent {
    val mongo8Database by inject<Mongo8Database>()
}

suspend fun initMongo8() {
    InitMongo8.mongo8Database.createIfMissing()
    InitMongo8.mongo8Database.start()

    val projectDatabases = getConfig().projects.flatMap { projectConfig -> projectConfig.infrastructure.databases?.mongo8?.databases.orEmpty() }.toSet()
    val existingDatabases = InitMongo8.mongo8Database.getDatabases() - "admin" - "config" - "local"
    val databasesToCreate = projectDatabases.filter { database -> !existingDatabases.contains(database) }
    databasesToCreate.forEach { database -> InitMongo8.mongo8Database.createDatabase(database) }

    val databasesToDelete = existingDatabases.filter { database -> !projectDatabases.contains(database) }
    databasesToDelete.forEach { database -> InitMongo8.mongo8Database.deleteDatabase(database) }
}