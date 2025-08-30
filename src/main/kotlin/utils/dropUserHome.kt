package dev.babies.utils

import kotlin.let
import kotlin.text.dropWhile
import kotlin.text.replaceFirst

fun String.dropUserHome() = replaceFirst(System.getProperty("user.home"), "")
    .dropWhile { it == '/' }
    .let { "~/$it" }