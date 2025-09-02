package dev.babies.application.model

import dev.babies.application.config.ProjectConfig
import dev.babies.application.os.host.DomainBuilder
import dev.babies.application.os.host.vocusDomain

class Project(
    val name: String,
    var modules: List<Module>
) {

    val projectDomain = DomainBuilder(name).buildAsSubdomain(
        skipIfSuffixAlreadyPresent = true,
        suffix = vocusDomain
    )

    companion object {
        fun fromConfig(projectConfig: ProjectConfig): Project {
            val project = Project(
                name = projectConfig.name,
                modules = emptyList()
            )
            projectConfig.modules.forEach { (moduleName, module) ->
                project.modules += Module.fromConfig(project, moduleName, module)
            }
            return project
        }
    }

    fun poweroff() {
        modules.forEach { it.poweroff() }
    }

    fun start() {
        modules.forEach {
            it.start()
        }
    }

    fun stop() {
        modules.forEach {
            it.poweroff()
        }
    }
}