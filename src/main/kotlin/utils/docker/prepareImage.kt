package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.PullResponseItem
import dev.babies.utils.red
import kotlin.collections.contains

private val helper: DockerAuthHelper = DockerAuthHelper()

fun DockerClient.prepareImage(image: String) {
    val hasLocal = listImagesCmd().exec().any { tags ->
        val t = tags.repoTags ?: emptyArray()
        t.contains(image)
    }

    val registryHost = helper.extractRegistry(image)
    val authConfig = helper.getAuthConfigForImage(image)

    // Try to pull (to get updates) even if we already have the image locally.
    // If we are offline and already have a local image, fall back to it without failing.
    try {
        val printBase = "Pulling Docker image: $image (registry: ${registryHost ?: "docker.io"})"
        print(printBase)
        val cmd = pullImageCmd(image)
            .withAuthConfig(authConfig)
        cmd.exec(
            object : PullImageResultCallback() {
                override fun onNext(item: PullResponseItem) {
                    super.onNext(item)
                    val current = item.progressDetail?.current ?: return
                    val total = item.progressDetail?.total ?: return
                    val percent = current.toDouble() / total.toDouble() * 100
                    print("\r$printBase ${"%.2f".format(percent)}%")
                }
                override fun onComplete() {
                    super.onComplete()
                    println()
                }
            }
        ).awaitCompletion()
    } catch (t: Throwable) {
        if (hasLocal) {
            println(red("Could not pull image '$image' (likely offline). Using locally available image."))
            return
        } else {
            throw t
        }
    }
}
