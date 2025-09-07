package dev.babies.application.init

import com.github.ajalt.clikt.core.BaseCliktCommand
import dev.babies.application.cli.completion.updateAutocomplete
import dev.babies.utils.gray
import dev.babies.utils.green
import dev.babies.utils.red
import dev.babies.utils.yellow
import kotlin.system.exitProcess

fun initCompletion(
    baseCommand: BaseCliktCommand<*>,
    shellChanged: Boolean = false,
) {
    if (needsInstall()) return

    var hasChangedShellConfig = shellChanged

    val currentShell = System.getenv("SHELL").ifBlank { null }
    if (currentShell == null) {
        println(red("No SHELL environment variable found. Please set it to the path of your default shell and restart this program."))
        exitProcess(1)
    }

    updateAutocomplete(
        baseCommand = baseCommand,
        currentShell = currentShell,
    ).let {
        hasChangedShellConfig = hasChangedShellConfig || it.hasChangedShellConfig
    }

    if (hasChangedShellConfig) {
        val reloadCommand = when (currentShell) {
            "/bin/zsh" -> "source ~/.zshrc"
            "/bin/bash" -> "source ~/.bashrc"
            else -> null
        }

        // Reload shell
        if (reloadCommand != null) {
            println("Reloading shell...")
            println(gray("> ") + green(reloadCommand))
            Runtime.getRuntime().exec(reloadCommand.split(" ").toTypedArray())
        } else {
            println(yellow("Please reload your shell to use autocomplete"))
        }
    }
}