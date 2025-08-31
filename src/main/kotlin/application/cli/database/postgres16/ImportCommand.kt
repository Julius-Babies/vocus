package dev.babies.application.cli.database.postgres16

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import dev.babies.application.database.postgres.p16.Postgres16
import dev.babies.utils.blue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ImportCommand(
    private val databaseName: String
) : SuspendingCliktCommand("import"), KoinComponent {

    private val database by inject<Postgres16>()

    val sqlDump by argument(
        name = "dump-file",
        help = "The SQL dump file to import"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)

    override suspend fun run() {
        println("Importing ${sqlDump.absolutePath} into ${blue("postgres16/$databaseName")}")

        database.importDatabase(dumpFile = sqlDump, database = databaseName)

        println("Done.")
    }
}