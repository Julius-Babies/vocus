package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.PullResponseItem
import dev.babies.utils.CHECK
import dev.babies.utils.HOUR_GLASS
import dev.babies.utils.REPLACE_LINE
import dev.babies.utils.red
import dev.babies.utils.yellow
import kotlin.system.exitProcess

private val helper: DockerAuthHelper = DockerAuthHelper()

fun DockerClient.prepareImage(image: String) {
    val hasLocal = listImagesCmd().exec().any { tags ->
        val t = tags.repoTags ?: emptyArray()
        t.contains(image)
    }

    val registryHost = helper.extractRegistry(image) ?: "docker.io"
    val authConfig = helper.getAuthConfigForImage(image)

    // Try to pull (to get updates) even if we already have the image locally.
    // If we are offline and already have a local image, fall back to it without failing.
    try {
        val printBase = "$HOUR_GLASS Pulling Docker image $image (registry: $registryHost)"
        print(printBase)
        var callbackException: Throwable? = null
        val cmd = pullImageCmd(image)
            .withAuthConfig(authConfig)
        var downloaded = false
        cmd.exec(
            object : PullImageResultCallback() {
                override fun onNext(item: PullResponseItem) {
                    super.onNext(item)
                    if (item.progressDetail == null) return
                    @Suppress("AssignedValueIsNeverRead")
                    downloaded = true
                    val current = item.progressDetail?.current ?: return
                    val total = item.progressDetail?.total ?: return
                    val percent = current.toDouble() / total.toDouble() * 100
                    print("$REPLACE_LINE$printBase ${"%.2f".format(percent)}%")
                }

                override fun onError(throwable: Throwable?) {
                    callbackException = throwable
                    this.close()
                    super.onError(null)
                }

                override fun onComplete() {
                    super.onComplete()
                    if (!downloaded) print(REPLACE_LINE)
                    else println("$REPLACE_LINE$CHECK Pulled Docker image $image (registry: $registryHost)")
                }
            }
        ).awaitCompletion()
        if (callbackException != null) throw callbackException!!
    } catch (_: NotFoundException) {
        println(REPLACE_LINE + red("Image $image not found, was using registry $registryHost"))
        exitProcess(1)
    } catch (t: Throwable) {
        if (hasLocal) {
            print(REPLACE_LINE + yellow("Could not pull image '$image' (likely offline). Using locally available image."))
            return
        } else {
            throw t
        }
    }
}
