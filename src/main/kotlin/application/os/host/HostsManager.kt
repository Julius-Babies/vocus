@file:OptIn(ExperimentalUuidApi::class)

package dev.babies.application.os.host

import dev.babies.application.os.SudoManager
import dev.babies.isDevelopment
import dev.babies.utils.gray
import dev.babies.utils.red
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val vocusDomain = if (isDevelopment) "local.vocusdev.internal" else "local.vocus.dev"

class HostsManager(): KoinComponent {
    private val sudoManager by inject<SudoManager>()
    private val hostsFile = File("/etc/hosts")

    companion object {
        private val BLOCK_START = if (isDevelopment) """### VOCUS DEV CONFIGURATION ###""" else """### VOCUS CONFIGURATION ###"""
        private val BLOCK_END = if (isDevelopment) """### END VOCUS DEV CONFIGURATION ###""" else """### END VOCUS CONFIGURATION ###"""
    }

    private fun getHostsContent(): List<String>? {
        return hostsFile
            .readLines()
            .also { if (it.none { line -> line == BLOCK_START }) return null }
            .dropWhile { it != BLOCK_START }
            .drop(1)
            .dropWhile { it.isBlank() }
            .dropLastWhile { it != BLOCK_END }
            .dropLast(1)
    }

    fun status(hostNames: Set<String>): List<HostStatus> {
        val content = getHostsContent().orEmpty().sorted()

        return hostNames.map { hostName ->
            val domain = DomainBuilder(hostName).buildAsSubdomain(suffix = vocusDomain)
            HostStatus(
                domain,
                content.any { line -> line.contains(domain) }
            )
        }
    }

    fun addHost(hostNames: Set<String>) {
        val hostNames = hostNames
            .map { DomainBuilder(it).apply { dropSuffix(vocusDomain) }.toString() }
            .toSet()
        val content = getHostsContent()
        if (content == null) {
            val block = "$BLOCK_START\n$BLOCK_END\n"
            val tempHostFile = File.createTempFile("hosts", ".tmp")
            val hostFileContent = hostsFile.readText() + "\n$block"
            tempHostFile.writeText(hostFileContent)

            if (!updateHosts(tempHostFile)) return
            addHost(hostNames)
            return
        }

        val newContent = content
            .plus(hostNames
                .map { DomainBuilder(it).buildAsSubdomain(suffix = vocusDomain) }
                .map { "127.0.0.1 $it" }
            )
            .distinct()
            .groupBy { it.split(" ").getOrNull(1)?.substringBefore(".$vocusDomain")?.substringAfterLast(".") }
            .minus(null)
            .toList()
            .sortedBy { it.first }
            .joinToString("\n\n") { it.second.joinToString("\n") }

        val tempHostFile = File.createTempFile("hosts${Uuid.random()}", ".tmp")
        val hostsFileContent = hostsFile.readText()

        // replace old content with new content
        val updatedHostsFileContent = hostsFileContent
            .replace(
                Regex("$BLOCK_START[\\s\\S]*?$BLOCK_END"),
                "$BLOCK_START\n$newContent\n$BLOCK_END"
            )
            .dropLastWhile { it == '\n' || it == '\r' || it == ' ' }
            .plus("\n")
        if (updatedHostsFileContent == hostsFileContent) return
        tempHostFile.writeText(updatedHostsFileContent)


        if (!updateHosts(tempHostFile)) return
    }

    fun removeHost(hostNames: Set<String>) {
        val hostNames = hostNames.map { DomainBuilder(it).dropSuffix(vocusDomain).toString() }.toSet()
        val content = getHostsContent() ?: return

        val newContent = content
            .filter { line -> hostNames.none { hostName -> line.contains("$hostName.$vocusDomain") } }
            .groupBy { it.split(" ").getOrNull(1)?.substringBefore(".$vocusDomain")?.substringAfterLast(".") }
            .minus(null)
            .toList()
            .sortedBy { it.first }
            .joinToString("\n\n") { it.second.joinToString("\n") }

        val tempHostFile = File.createTempFile("hosts${Uuid.random()}", ".tmp")
        val hostsFileContent = hostsFile.readText()

        // replace old content with new content
        val updatedHostsFileContent = hostsFileContent
            .replace(
                Regex("$BLOCK_START[\\s\\S]*?$BLOCK_END"),
                if (newContent.isBlank()) "" else "$BLOCK_START\n$newContent\n$BLOCK_END"
            )
            .dropLastWhile { it == '\n' || it == '\r' || it == ' ' }
            .plus("\n")
        if (updatedHostsFileContent == hostsFileContent) return
        tempHostFile.writeText(updatedHostsFileContent)

        if (!updateHosts(tempHostFile)) return
    }

    fun getHosts(): Set<String> {
        val content = getHostsContent() ?: return emptySet()
        return content.mapNotNull { it.split(" ").getOrNull(1) }.toSet()
    }

    private fun updateHosts(tempFile: File): Boolean {
        println("The new hosts file will look like this:")
        println(tempFile.readText())
        println()
        println(gray("Continue [y/n]? (CTRL+C for canceling)"))
        val shouldContinue = readln().trim().equals("y", ignoreCase = true)
        if (shouldContinue) {
            println(red("We will modify the hosts file. You may have to enter your password first."))
            val password = sudoManager.get()
            val command = "echo '$password' | sudo -S sh -c \"mv ${tempFile.absolutePath} ${hostsFile.absolutePath}\""
            Runtime.getRuntime().exec(arrayOf("sh", "-c", command)).waitFor()
        }

        return shouldContinue
    }
}

data class HostStatus(
    val host: String,
    val active: Boolean
)