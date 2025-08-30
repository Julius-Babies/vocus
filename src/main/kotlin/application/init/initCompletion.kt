package dev.babies.application.init

import com.github.ajalt.clikt.core.BaseCliktCommand
import dev.babies.application.cli.completion.updateAutocomplete
import dev.babies.applicationDirectory
import dev.babies.utils.*
import org.kotlincrypto.hash.sha1.SHA1
import java.io.File
import kotlin.system.exitProcess

val appFile = applicationDirectory.resolve("app.jar")
val alias = $$"""vocus() {
  java -jar $${applicationDirectory.absolutePath.dropUserHome()} $@
}
"""

fun needsInstall(): Boolean {
    val currentPath = JarLocation().jarLocation

    val isRunningFromJar = currentPath.endsWith(".jar")
    if (!isRunningFromJar) return false

    if (!appFile.exists()) return true

    val appFileHash = appFile.readBytes().let {
        val sha1 = SHA1()
        sha1.update(it)
        sha1.digest().toHexString()
    }

    val thisFileHash = File(currentPath).readBytes().let {
        val sha1 = SHA1()
        sha1.update(it)
        sha1.digest().toHexString()
    }

    if (appFileHash != thisFileHash) return true
    return false
}

fun initCompletion(
    baseCommand: BaseCliktCommand<*>
) {
    val currentPath = JarLocation().jarLocation
    if (!needsInstall()) return

    var hasChangedShellConfig = false
    var isInstalled = true

    val currentShell = System.getenv("SHELL").ifBlank { null }
    if (currentShell == null) {
        println(red("No SHELL environment variable found. Please set it to the path of your default shell and restart this program."))
        exitProcess(1)
    }

    if (currentPath != appFile.absolutePath) {
        isInstalled = false
        println(green("✨ We're installing vocus into your user home so you can access it from any directory."))
        println("Installing into " + gray(applicationDirectory.absolutePath))
        println("Copying jar file from ${gray(currentPath.dropUserHome())} to ${gray(appFile.absolutePath.dropUserHome())}")
        val currentFile = File(currentPath)
        currentFile.copyTo(appFile, overwrite = true)

        when (currentShell) {
            "/bin/zsh" -> {
                val zshrcFile = File(System.getProperty("user.home") + "/.zshrc")
                val content = zshrcFile.readText()
                if (alias !in content) {
                    println("Appending alias to your zshrc file:")
                    println(alias.prependIndent("  "))
                    zshrcFile.appendText("\n" + alias)
                    hasChangedShellConfig = true
                }
            }
            "/bin/bash" -> {
                val bashrcFile = File(System.getProperty("user.home") + "/.bashrc")
                val lines = bashrcFile.readLines()
                if (alias !in lines) {
                    println("Appending alias to your bashrc file:")
                    println(alias.prependIndent("  "))
                    bashrcFile.appendText("\n" + alias)
                    hasChangedShellConfig = true
                }
            }
        }
    }

    updateAutocomplete(
        baseCommand = baseCommand,
        currentShell = currentShell,
    ).let {
        hasChangedShellConfig = hasChangedShellConfig || it.hasChangedShellConfig
    }

    if (hasChangedShellConfig) {
        if (isInstalled) {
            println()
            println(green("\uD83C\uDF89 Installation of vocus complete."))
        }
        println(buildString {
            append("Please restart your shell using ")
            append(green(when (currentShell) {
                "/bin/zsh" -> "source ~/.zshrc"
                "/bin/bash" -> "source ~/.bashrc"
                else -> "your shell specific command"
            }))
        })
    }
}