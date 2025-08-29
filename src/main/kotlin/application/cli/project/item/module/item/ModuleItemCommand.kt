package dev.babies.application.cli.project.item.module.item

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import dev.babies.application.cli.project.item.module.ModuleItemContext

class ModuleItemCommand(
    moduleItemContext: ModuleItemContext
) : SuspendingCliktCommand(moduleItemContext.moduleName) {
    override suspend fun run() {

    }

    init {
        subcommands(
            SetStateCommand(moduleItemContext)
        )
    }
}