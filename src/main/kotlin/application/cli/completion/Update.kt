package dev.babies.application.cli.completion

import com.github.ajalt.clikt.completion.CompletionGenerator
import com.github.ajalt.clikt.core.BaseCliktCommand
import dev.babies.applicationDirectory
import dev.babies.isDevelopment
import org.kotlincrypto.hash.sha1.SHA1
import java.io.File
import dev.babies.application.cli.completion.getConfig as getAutocompleteConfig

fun updateAutocomplete(
    baseCommand: BaseCliktCommand<*>,
    currentShell: String
): UpdateAutocompleteResult {
    val config = getAutocompleteConfig()

    var hasChangedShellConfig = false
    when (currentShell) {
        "/bin/zsh" -> {
            val autocomplete = CompletionGenerator.generateCompletionForCommand(baseCommand, currentShell)
            val hash = run {
                val sha1 = SHA1()
                sha1.update(autocomplete.toByteArray())
                sha1.digest().toHexString()
            }
            val hasAutocompleteChanged = hash != config.zsh.lastCommandHash
            if (hasAutocompleteChanged) {
                if (config.zsh.lastCommandHash == null) println("Installing autocomplete")
                else println("Updating autocomplete")
                applicationDirectory.resolve("autocomplete.zsh").writeText(autocomplete)
                writeConfig(config.copy(zsh = config.zsh.copy(lastCommandHash = hash)))
                hasChangedShellConfig = true
            }

            val zshrcFile = File(System.getProperty("user.home") + "/.zshrc")
            val lines = zshrcFile.readLines()
            val hasAutocomplete = lines.any { line ->
                line == "source ${applicationDirectory.resolve("autocomplete.zsh").absolutePath}"
            }
            if (!hasAutocomplete) {
                println("Adding autocomplete to your zshrc file")
                if (!isDevelopment) zshrcFile.appendText("\nsource ${applicationDirectory.resolve("autocomplete.zsh").absolutePath}")
                hasChangedShellConfig = true
            }
        }
    }

    return UpdateAutocompleteResult(hasChangedShellConfig)
}

data class UpdateAutocompleteResult(
    val hasChangedShellConfig: Boolean
)