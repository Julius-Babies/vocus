package dev.babies.utils

class JarLocation {
    val jarLocation: String = JarLocation::class.java.protectionDomain.codeSource.location.toURI().path
}