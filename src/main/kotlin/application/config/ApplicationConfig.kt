package dev.babies.application.config

import com.charleskorn.kaml.Yaml
import dev.babies.applicationDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class ApplicationConfig(
    @SerialName("projects") var projects: List<ProjectConfig> = listOf()
)

private val configFile = applicationDirectory.resolve("config.yaml")
fun getConfig(): ApplicationConfig {
    if (!configFile.exists()) return ApplicationConfig()
    val content = configFile.readText()
    return Yaml.default.decodeFromString(content)
}

internal fun writeConfig(config: ApplicationConfig) {
    configFile.writeText(Yaml.default.encodeToString(config))
}

private val updateMutex = Mutex()
internal suspend fun updateConfig(block: (config: ApplicationConfig) -> ApplicationConfig) {
    updateMutex.withLock {
        val config = getConfig()
        writeConfig(block(config))
    }
}