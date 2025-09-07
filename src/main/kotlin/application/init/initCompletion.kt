package dev.babies.application.init

import com.github.ajalt.clikt.core.BaseCliktCommand
import dev.babies.application.cli.completion.updateAutocomplete
import dev.babies.utils.dropUserHome
import dev.babies.utils.gray
import dev.babies.utils.green
import dev.babies.utils.red
import dev.babies.utils.yellow
import java.nio.charset.StandardCharsets
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
            val process = ProcessBuilder(currentShell, reloadCommand).inheritIO().start()
            process.waitFor()
            println(red(process.errorStream.bufferedReader().readText()))
            println(green(process.inputStream.bufferedReader().readText()))
            println("If you receive an error like " + yellow("/bin/zsh: can't open input file: ~/.vocus/autocomplete.zsh") + " and you are using macOS, make sure to grant the java binary full disk access in system settings. See " + yellow("https://apple.stackexchange.com/questions/402132/cronjobs-do-not-run/402179#402179") + " for a similar problem.")
            println("Reload complete.")
        } else {
            println(yellow("Please reload your shell to use autocomplete"))
        }
    }
}