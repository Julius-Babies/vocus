package dev.babies.utils.domain

/**
 * Replaces spaces and other special characters so that
 * the name can be used as a (sub)domain
 */
fun String.nameToDomain(): String {
    return this.lowercase()
        .replace(" ", "-")
}