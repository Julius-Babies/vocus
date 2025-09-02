package dev.babies.utils.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import dev.babies.utils.red

fun DockerClient.runCommand(containerId: String, command: List<String>): CommandResult {
    try {
        val exec = this.execCreateCmd(containerId)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd(*command.toTypedArray())
            .exec()

        val output = StringBuilder()
        val execResult = this
            .execStartCmd(exec.id)
            .exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame) {
                    val payload = String(frame.payload).dropLastWhile { it == '\n' || it == '\r' }
                    when (frame.streamType) {
                        StreamType.STDERR -> {
                            if (!payload.startsWith("NOTICE")) println(red(payload))
                        }
                        StreamType.STDOUT -> output.append(payload)
                        else -> Unit
                    }
                }
            })
        execResult.awaitCompletion()

        val inspectResponse = this.inspectExecCmd(exec.id).exec()

        return CommandResult(inspectResponse.exitCodeLong?.toInt() ?: 0, output.toString())
    } catch (e: Exception) {
        println(red("Error executing command in container $containerId: ${e.stackTraceToString()}"))
        throw e
    }
}

data class CommandResult(val exitCode: Int, val output: String)