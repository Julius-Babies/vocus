package dev.babies.application.cli.project.item.module.item

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.enum
import dev.babies.application.cli.project.item.module.ModuleItemContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent

class SetStateCommand(
    private val moduleItemContext: ModuleItemContext
) : SuspendingCliktCommand("set-state"), KoinComponent {

    val target by argument(
        "TARGET",
        help = "The target module which contains the item state"
    ).enum<State>(ignoreCase = true, key = { it.name.lowercase() })

    @Serializable
    enum class State {
        @SerialName("off") Off,
        @SerialName("docker") Docker,
        @SerialName("local") Local
    }

    override suspend fun run() {
        println("Set ${moduleItemContext.module.name} state to $target")
        moduleItemContext.module.setState(when(target) {
            State.Off -> dev.babies.application.model.Module.State.Off
            State.Docker -> dev.babies.application.model.Module.State.Docker
            State.Local -> dev.babies.application.model.Module.State.Local
        })
    }
}