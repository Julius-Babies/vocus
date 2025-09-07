package dev.babies.application.init

import com.github.ajalt.clikt.core.BaseCliktCommand
import com.sun.jna.Platform
import dev.babies.application.cli.completion.updateAutocomplete
import dev.babies.utils.dropUserHome
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

    val updateResult = updateAutocomplete(
        baseCommand = baseCommand,
        currentShell = currentShell,
    ).also {
        hasChangedShellConfig = hasChangedShellConfig || it.hasChangedShellConfig
    }

    if (hasChangedShellConfig) {
        val reloadCommand = when (currentShell) {
            "/bin/zsh" -> updateResult.file?.absolutePath?.dropUserHome() ?: "-i"
            "/bin/bash" -> "-i"
            else -> null
        }

        // Reload shell
        if (reloadCommand != null) {
            println("Reloading shell...")
            println(gray("> ") + green("$currentShell $reloadCommand"))
            val process = ProcessBuilder(currentShell, reloadCommand).start()
            val errorText = process.errorStream.bufferedReader().readText().let {
                it.dropLastWhile { c -> c in setOf('\n', '\r', ' ') }
            }
            process.waitFor()
            if (errorText.isNotBlank()) {
                println(red("Failed to reload shell: '$errorText'"))
                if (errorText == "/bin/zsh: can't open input file: ~/.vocus/autocomplete.zsh") {
                    if (Platform.isMac()) {
                        println(yellow("The process couldn't read the autocomplete file, so a full shell reload will be run every time the autocomplete changes."))
                        println(yellow("To prevent this, please grant the java binary full disk access in system settings. See https://apple.stackexchange.com/questions/402132/cronjobs-do-not-run/402179#402179 for a similar problem."))
                        println(yellow("  1. Find out, which java binary is used: ") + gray("which java"))
                        println(yellow("  2. Grant full disk access to this binary in system settings."))
                        println(yellow("    -> Privacy & Security"))
                        println(yellow("    -> Full disk access"))
                        println(yellow("    -> Add java binary (via the little plus icon)"))
                    }
                }
            } else println("Reload complete.")
        } else {
            println(yellow("Please reload your shell to use autocomplete"))
        }
    }
}