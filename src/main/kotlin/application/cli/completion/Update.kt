package dev.babies.application.cli.completion

import com.github.ajalt.clikt.completion.CompletionGenerator
import com.github.ajalt.clikt.core.BaseCliktCommand
import dev.babies.applicationDirectory
import dev.babies.isDevelopment
import dev.babies.utils.red
import org.kotlincrypto.hash.sha1.SHA1
import java.io.File

fun updateAutocomplete(
    baseCommand: BaseCliktCommand<*>,
    currentShell: String
): UpdateAutocompleteResult {

    var hasChangedShellConfig = false
    val autocompleteFile: File
    when (currentShell) {
        "/bin/zsh" -> {
            autocompleteFile = applicationDirectory.resolve("autocomplete.zsh")
            val autocomplete = CompletionGenerator.generateCompletionForCommand(baseCommand, currentShell)
            val hash = run {
                val sha1 = SHA1()
                sha1.update(autocomplete.toByteArray())
                sha1.digest().toHexString()
            }
            val hasAutocompleteChanged = !autocompleteFile.exists() || hash != autocompleteFile.readText().let {
                val sha1 = SHA1()
                sha1.update(it.toByteArray())
                sha1.digest().toHexString()
            }
            if (hasAutocompleteChanged) {
                if (!autocompleteFile.exists()) println("Installing autocomplete")
                else println("Updating autocomplete")
                autocompleteFile.writeText(autocomplete)
                hasChangedShellConfig = true
            }

            val zshrcFile = File(System.getProperty("user.home") + "/.zshrc")
            val lines = zshrcFile.readLines()
            val hasAutocomplete = lines.any { line ->
                line == "source ${autocompleteFile.absolutePath}"
            }
            if (!hasAutocomplete) {
                println("Adding autocomplete to your zshrc file")
                if (!isDevelopment) zshrcFile.appendText("\nsource ${autocompleteFile.absolutePath}")
                hasChangedShellConfig = true
            }
        }

        else -> {
            println(red("Your shell is not supported. Only zsh is supported at the moment."))
            return UpdateAutocompleteResult(false, null)
        }
    }

    return UpdateAutocompleteResult(
        hasChangedShellConfig = hasChangedShellConfig,
        file = autocompleteFile
    )
}

data class UpdateAutocompleteResult(
    val hasChangedShellConfig: Boolean,
    val file: File?
)