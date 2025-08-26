package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import org.slf4j.Logger

fun DockerClient.runCommand(containerId: String, command: List<String>, logger: Logger? = null): Int {
    logger?.info("Running command in container $containerId: ${command.joinToString(" ")}")
    try {
        val exec = this.execCreateCmd(containerId)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd(*command.toTypedArray())
            .exec()

        val execResult = this
            .execStartCmd(exec.id)
            .exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame) {
                    val payload = String(frame.payload).dropLastWhile { it == '\n' || it == '\r' }
                    when (frame.streamType) {
                        StreamType.STDOUT -> logger?.info(payload)
                        StreamType.STDERR -> {
                            if (payload.startsWith("NOTICE")) logger?.warn(payload)
                            else logger?.error(payload)
                        }
                        else -> logger?.debug("Unknown stream type: {}", frame.streamType)
                    }
                }
            })
        execResult.awaitCompletion()

        val inspectResponse = this.inspectExecCmd(exec.id).exec()

        logger?.info("Command executed successfully in container $containerId")
        return inspectResponse.exitCodeLong?.toInt() ?: 0
    } catch (e: Exception) {
        logger?.error("Error executing command in container $containerId: ${e.message}", e)
        throw e
    }
}