@file:OptIn(ExperimentalUuidApi::class)

package dev.babies.application.os.host

import dev.babies.application.os.SudoManager
import dev.babies.utils.gray
import dev.babies.utils.red
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.also
import kotlin.collections.any
import kotlin.collections.distinct
import kotlin.collections.drop
import kotlin.collections.dropLast
import kotlin.collections.dropLastWhile
import kotlin.collections.dropWhile
import kotlin.collections.groupBy
import kotlin.collections.joinToString
import kotlin.collections.map
import kotlin.collections.minus
import kotlin.collections.none
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.collections.sorted
import kotlin.collections.sortedBy
import kotlin.collections.toList
import kotlin.collections.toSet
import kotlin.io.readLines
import kotlin.io.readText
import kotlin.io.writeText
import kotlin.text.contains
import kotlin.text.equals
import kotlin.text.isBlank
import kotlin.text.lowercase
import kotlin.text.removeSuffix
import kotlin.text.replace
import kotlin.text.split
import kotlin.text.substringAfterLast
import kotlin.text.substringBefore
import kotlin.text.trim
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class HostsManager(): KoinComponent {
    private val sudoManager by inject<SudoManager>()
    private val hostsFile = File("/etc/hosts")

    companion object {
        private const val BLOCK_START = """### VOCUS CONFIGURATION ###"""
        private const val BLOCK_END = """### END VOCUS CONFIGURATION ###"""
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
            HostStatus(
                "$hostName.local.vocus.dev",
                content.any { line -> line.contains("$hostName.local.vocus.dev") }
            )
        }
    }

    fun addHost(hostNames: Set<String>) {
        val hostNames = hostNames
            .map { it.lowercase().removeSuffix(".local.vocus.dev") }.toSet()
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
            .plus(hostNames.map { "127.0.0.1 $it.local.vocus.dev" })
            .distinct()
            .groupBy { it.split(" ").getOrNull(1)?.substringBefore(".local.vocus.dev")?.substringAfterLast(".") }
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
        val hostNames = hostNames.map { it.lowercase().removeSuffix(".local.vocus.dev") }.toSet()
        val content = getHostsContent() ?: return

        val newContent = content
            .filter { line -> hostNames.none { hostName -> line.contains("$hostName.local.vocus.dev") } }
            .groupBy { it.split(" ").getOrNull(1)?.substringBefore(".local.vocus.dev")?.substringAfterLast(".") }
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