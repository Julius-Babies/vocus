package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageResultCallback
import org.slf4j.Logger
import kotlin.collections.contains

private val helper: DockerAuthHelper = DockerAuthHelper()

fun DockerClient.prepareImage(image: String, logger: Logger? = null) {
    val hasLocal = listImagesCmd().exec().any { tags ->
        val t = tags.repoTags ?: emptyArray()
        t.contains(image)
    }

    val registryHost = helper.extractRegistry(image)
    val authConfig = helper.getAuthConfigForImage(image)

    // Try to pull (to get updates) even if we already have the image locally.
    // If we are offline and already have a local image, fall back to it without failing.
    try {
        logger.infoWithFallback("Pulling Docker image: $image (registry: ${registryHost ?: "docker.io"})")
        var cmd = pullImageCmd(image)
        if (authConfig != null) {
            cmd = cmd.withAuthConfig(authConfig)
            logger.infoWithFallback("Using credentials for registry: ${authConfig.registryAddress}")
        }
        cmd.exec(PullImageResultCallback()).awaitCompletion()
    } catch (t: Throwable) {
        if (hasLocal) {
            logger.warnWithFallback("Could not pull image '$image' (likely offline). Using locally available image.")
            return
        } else {
            // No local copy available: rethrow to signal failure
            throw t
        }
    }
}

private fun Logger?.infoWithFallback(message: String) = this?.info(message) ?: println(message)
private fun Logger?.warnWithFallback(message: String) = this?.warn(message) ?: println(message)