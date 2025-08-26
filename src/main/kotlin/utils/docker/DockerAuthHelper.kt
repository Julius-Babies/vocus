package dev.babies.utils.docker

import com.github.dockerjava.api.model.AuthConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

class DockerAuthHelper {
    private val rawConfig: String
    private val json: JsonObject

    init {
        val path = System.getProperty("user.home") + "/.docker/config.json"
        val (text, parsed) = try {
            val t = Files.readString(Paths.get(path))
            t to Json.Default.parseToJsonElement(t).jsonObject
        } catch (_: Throwable) {
            // If config is missing/malformed, proceed with an empty JSON
            "{}" to Json.Default.parseToJsonElement("{}").jsonObject
        }
        rawConfig = text
        json = parsed
    }

    /**
     * Order of resolution:
     * 1) auths&#91;registry&#93;
     * 2) credHelpers&#91;registry&#93;
     * 3) global credsStore helper.
     */
    fun getAuthConfigForImage(image: String): AuthConfig? {
        val registryHost = extractRegistry(image)

        // Determine candidate keys under "auths" for this registry.
        // Docker Hub has multiple common representations in config.json.
        val candidateKeys = buildList {
            if (registryHost == null) {
                add("https://index.docker.io/v1/")
                add("index.docker.io")
                add("docker.io")
            } else {
                add(registryHost)
                add("https://$registryHost")
                add("https://$registryHost/")
                add("http://$registryHost")
                add("http://$registryHost/")
            }
        }

        // 1) Try direct auths entries (either username/password or base64 "auth").
        val auths = json["auths"]?.jsonObject
        if (auths != null) {
            for (key in candidateKeys) {
                val entry = auths[key]?.jsonObject ?: continue
                val (user, pass) = extractUserPass(entry)
                if (user != null && pass != null) {
                    val addr = ensureUrl(key)
                    return AuthConfig()
                        .withRegistryAddress(addr)
                        .withUsername(user)
                        .withPassword(pass)
                }
            }
        }

        // 2) Registry-specific credential helper.
        val helpers = json["credHelpers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        if (registryHost != null && helpers.containsKey(registryHost)) {
            return fetchFromHelper(helpers[registryHost]!!, ensureUrl(registryHost))
        }
        // Docker Hub via helper may be keyed as "index.docker.io".
        if (registryHost == null && helpers.containsKey("index.docker.io")) {
            return fetchFromHelper(helpers["index.docker.io"]!!, "https://index.docker.io/v1/")
        }

        // 3) Global credsStore fallback.
        val store = json["credsStore"]?.jsonPrimitive?.content
        if (store != null) {
            val server = if (registryHost == null) "https://index.docker.io/v1/" else ensureUrl(registryHost)
            return fetchFromHelper(store, server)
        }

        // 4) No credentials found → anonymous access.
        return null
    }

    private fun extractUserPass(entry: JsonObject): Pair<String?, String?> {
        var user = entry["username"]?.jsonPrimitive?.content
        var pass = entry["password"]?.jsonPrimitive?.content
        if (user.isNullOrBlank() || pass.isNullOrBlank()) {
            val encoded = entry["auth"]?.jsonPrimitive?.content
            if (!encoded.isNullOrBlank()) {
                try {
                    val decoded = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
                    val idx = decoded.indexOf(":")
                    if (idx > 0) {
                        user = decoded.take(idx)
                        pass = decoded.substring(idx + 1)
                    }
                } catch (_: Throwable) { /* ignore malformed base64 */ }
            }
        }
        if (user.isNullOrBlank() || pass.isNullOrBlank()) return null to null
        return user to pass
    }

    @Throws(Exception::class)
    private fun fetchFromHelper(helper: String, serverUrl: String): AuthConfig? {
        val helperBinary = "docker-credential-$helper"
        val process = ProcessBuilder(helperBinary, "get").start()
        BufferedWriter(OutputStreamWriter(process.outputStream)).use { writer ->
            // Most helpers expect a full URL (e.g., https://index.docker.io/v1/)
            writer.write(serverUrl)
            writer.newLine()
            writer.flush()
        }
        val output: String = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.readText()
        }
        // Best-effort JSON parse of helper output.
        val credsJson = try { Json.Default.parseToJsonElement(output).jsonObject } catch (_: Throwable) { null } ?: return null
        val server = credsJson["ServerURL"]?.jsonPrimitive?.content ?: serverUrl
        val username = credsJson["Username"]?.jsonPrimitive?.content ?: return null
        val secret = credsJson["Secret"]?.jsonPrimitive?.content ?: return null
        return AuthConfig()
            .withRegistryAddress(ensureUrl(server))
            .withUsername(username)
            .withPassword(secret)
    }

    /**
     * Extracts the registry host from an image name.
     * Examples:
     * - "alpine:latest" → null (Docker Hub)
     * - "registry.gitlab.com/foo/bar:tag" → "registry.gitlab.com"
     */
    fun extractRegistry(image: String): String? {
        val parts = image.split("/", limit = 2)
        // No dot/colon/localhost in the first segment usually means Docker Hub (implicit registry)
        return if (!parts[0].contains(".") && !parts[0].contains(":") && parts[0] != "localhost") {
            null
        } else parts[0]
    }

    private fun ensureUrl(key: String): String {
        val k = key.trim()
        return if (k.startsWith("http://") || k.startsWith("https://")) k else "https://$k"
    }
}