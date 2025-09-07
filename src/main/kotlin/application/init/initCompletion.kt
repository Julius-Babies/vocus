package dev.babies.application.init

import com.github.ajalt.clikt.core.BaseCliktCommand
import dev.babies.application.cli.completion.updateAutocomplete
import dev.babies.utils.red
import kotlin.system.exitProcess

fun initCompletion(
    baseCommand: BaseCliktCommand<*>,
) {
    if (needsInstall()) return

    val currentShell = System.getenv("SHELL").ifBlank { null }
    if (currentShell == null) {
        println(red("No SHELL environment variable found. Please set it to the path of your default shell and restart this program."))
        exitProcess(1)
    }

    updateAutocomplete(
        baseCommand = baseCommand,
        currentShell = currentShell,
    )
}