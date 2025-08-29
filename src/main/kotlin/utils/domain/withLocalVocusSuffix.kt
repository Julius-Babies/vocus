package dev.babies.utils.domain

fun String.withLocalVocusSuffix(): String {
    if (endsWith(".local.vocus.dev")) return this
    return "$this.local.vocus.dev"
}