package dev.babies.application.init

import dev.babies.applicationDirectory
import dev.babies.isDevelopment
import dev.babies.utils.JarLocation
import dev.babies.utils.dropUserHome
import dev.babies.utils.gray
import dev.babies.utils.green
import dev.babies.utils.red
import org.kotlincrypto.hash.sha1.SHA1
import java.io.File
import kotlin.system.exitProcess

val appFile = applicationDirectory.resolve("app.jar")
val alias = $$"""vocus() {
  java -jar $${applicationDirectory.absolutePath.dropUserHome()}/app.jar $@
  source $${applicationDirectory.resolve("autocomplete.zsh").absolutePath}
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

fun install() {
    if (!needsInstall()) return
    val currentPath = JarLocation().jarLocation

    val currentShell = System.getenv("SHELL").ifBlank { null }
    if (currentShell == null) {
        println(red("No SHELL environment variable found. Please set it to the path of your default shell and restart this program."))
        exitProcess(1)
    }

    if (currentPath != appFile.absolutePath) {
        println(green("✨ We're installing vocus into your user home so you can access it from any directory."))
        println("Installing into " + gray(applicationDirectory.absolutePath))
        println("Copying jar file from ${gray(currentPath.dropUserHome())} to ${gray(appFile.absolutePath.dropUserHome())}")
        val currentFile = File(currentPath)
        if (!isDevelopment) currentFile.copyTo(appFile, overwrite = true)

        when (currentShell) {
            "/bin/zsh" -> {
                val zshrcFile = File(System.getProperty("user.home") + "/.zshrc")
                val content = zshrcFile.readText()
                if (alias !in content) {
                    println("Appending alias to your zshrc file:")
                    println(alias.prependIndent("  "))
                    if (!isDevelopment) zshrcFile.appendText("\n" + alias)
                }
            }
            "/bin/bash" -> {
                val bashrcFile = File(System.getProperty("user.home") + "/.bashrc")
                val lines = bashrcFile.readLines()
                if (alias !in lines) {
                    println("Appending alias to your bashrc file:")
                    println(alias.prependIndent("  "))
                    if (!isDevelopment) bashrcFile.appendText("\n" + alias)
                }
            }
        }

        println()
        println(green("\uD83C\uDF89 Installation of vocus complete."))
    }
}