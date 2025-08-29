package dev.babies.application.init

import dev.babies.application.database.postgres.pgadmin.Pgadmin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object InitPgAdmin : KoinComponent {
    val pgAdmin by inject<Pgadmin>()
}

suspend fun initPgAdmin() {
    InitPgAdmin.pgAdmin.createIfMissing()
    InitPgAdmin.pgAdmin.start()
}