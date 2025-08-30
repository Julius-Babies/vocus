package dev.babies.application.cli.completion

import dev.babies.applicationDirectory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Config(
    @SerialName("bash") val bash: ShellConfig = ShellConfig(),
    @SerialName("zsh") val zsh: ShellConfig = ShellConfig(),
    @SerialName("fish") val fish: ShellConfig = ShellConfig(),
) {

    @Serializable
    data class ShellConfig(
        @SerialName("last_command_hash") val lastCommandHash: String? = null
    )
}

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
    isLenient = true
    ignoreUnknownKeys = true
}

val configFile = applicationDirectory.resolve("completion.json")

private var config = loadConfig()

private fun loadConfig(): Config = configFile.let { file ->
    if (!file.exists()) Config()
    else try {
        json.decodeFromString<Config>(file.readText())
    } catch (_: Exception) {
        file.writeText(json.encodeToString(Config()))
        Config()
    }
}

fun getConfig(): Config = config
fun writeConfig(newConfig: Config) {
    configFile.writeText(json.encodeToString(newConfig))
    config = loadConfig()
}