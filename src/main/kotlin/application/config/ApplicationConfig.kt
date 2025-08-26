package dev.babies.application.config

import com.charleskorn.kaml.Yaml
import dev.babies.applicationDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class ApplicationConfig(
    @SerialName("projects") val projects: List<ProjectConfig> = listOf()
)

private val configFile = applicationDirectory.resolve("config.yaml")
fun getConfig(): ApplicationConfig {
    if (!configFile.exists()) return ApplicationConfig()
    val content = configFile.readText()
    return Yaml.default.decodeFromString(content)
}

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

internal fun writeConfig(config: ApplicationConfig) {
    configFile.writeText(json.encodeToString(config))
}

private val updateMutex = Mutex()
internal suspend fun updateConfig(block: (config: ApplicationConfig) -> ApplicationConfig) {
    updateMutex.withLock {
        val config = getConfig()
        writeConfig(block(config))
    }
}