package dev.babies.application.cli.ssl

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import dev.babies.application.os.host.vocusDomain
import dev.babies.applicationDirectory
import dev.babies.utils.gray
import dev.babies.utils.green
import dev.babies.utils.red
import dev.babies.utils.yellow

class SslCommand : SuspendingCliktCommand(name = "ssl") {
    override suspend fun run() {
        println("We have created a local CA for you to issue certificates.")
        println("Ever project gets its own certificate, but they are all signed by the same CA.")
        println("You can add the CA to your system or browser to avoid security warnings.")
        println()
        println(yellow("Java Apps"))
        println("If a Java app is connecting to a domain belonging to " + gray(vocusDomain) + ", it needs to know")
        println("our Root-CA.")
        val trustStoreFile = applicationDirectory
            .resolve("ssl")
            .resolve("root-ca.jks")
        if (!trustStoreFile.exists()) {
            println(red("The truststore file does not yet exists. We've looked at ${trustStoreFile.absolutePath}."))
            println("Run " + gray("vocus boot") + " to generate the the Root-CA, certificates and some other files.")
            return
        }
        println("Add the Java Keystore-Arguments to your java command:")
        println(green("-Djavax.net.ssl.trustStore=${trustStoreFile.absolutePath} -Djavax.net.ssl.trustStorePassword=vocus"))
    }
}