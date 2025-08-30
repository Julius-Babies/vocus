package dev.babies.utils.domain

fun String.withLocalVocusSuffix(): String {
    if (isEmpty()) return "local.vocus.dev"
    if (endsWith(".local.vocus.dev")) return this
    return "$this.local.vocus.dev"
}