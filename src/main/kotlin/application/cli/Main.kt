package dev.babies.application.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import dev.babies.application.cli.project.ProjectCommand
import dev.babies.application.init.initCompletion
import dev.babies.application.init.needsInstall
import dev.babies.utils.gray
import dev.babies.utils.green
import java.io.File

class Main : SuspendingCliktCommand("vocus") {
    override val invokeWithoutSubcommand: Boolean = true
    val helpFlag by option("-h", "--help", help = "Show this help message and exit").flag()
    override suspend fun run() {
        if (needsInstall()) {
            initCompletion(this)
            return
        }
        initCompletion(this)

        if (helpFlag) {
            println(getFormattedHelp())
            return
        }

        println("Welcome to " + green("Vocus") + "!")
        println()
        println("Vocus is a tool to manage local development environments for multiple projects.")
        println("To get started, use the ${gray("vocus project register")} command inside a project "+
                "containing a Vocusfile.")
        println("Every subcommand provides a detailed help message with the ${gray("-h")}-flag. Try it out " +
                "with ${gray("vocus -h")}!")
        println()
        println("By the way, every time you run a vocus command, the tab-completion is updated automatically.")
    }


    init {
        val context = CommandContext(
            executionDirectory = File("."),
        )

        subcommands(ProjectCommand(context))
    }
}

open class CommandContext(
    val executionDirectory: File
)