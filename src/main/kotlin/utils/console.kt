@file:Suppress("unused")

package dev.babies.utils

object ConsoleStyle {
    const val RESET = "\u001B[0m"
    const val BLACK = "\u001B[30m"
    const val AQUA = "\u001B[33m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val PURPLE = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"
    const val GRAY = "\u001B[90m"
    const val BOLD = "\u001B[1m"
    const val UNDERLINE = "\u001B[4m"
}

fun black(content: String): String = "${ConsoleStyle.BLACK}$content${ConsoleStyle.RESET}"
fun aqua(content: String): String = "${ConsoleStyle.AQUA}$content${ConsoleStyle.RESET}"
fun red(content: String): String = "${ConsoleStyle.RED}$content${ConsoleStyle.RESET}"
fun green(content: String): String = "${ConsoleStyle.GREEN}$content${ConsoleStyle.RESET}"
fun yellow(content: String): String = "${ConsoleStyle.YELLOW}$content${ConsoleStyle.RESET}"
fun blue(content: String): String = "${ConsoleStyle.BLUE}$content${ConsoleStyle.RESET}"
fun purple(content: String): String = "${ConsoleStyle.PURPLE}$content${ConsoleStyle.RESET}"
fun cyan(content: String): String = "${ConsoleStyle.CYAN}$content${ConsoleStyle.RESET}"
fun white(content: String): String = "${ConsoleStyle.WHITE}$content${ConsoleStyle.RESET}"
fun gray(content: String): String = "${ConsoleStyle.GRAY}$content${ConsoleStyle.RESET}"
fun bold(content: String): String = "${ConsoleStyle.BOLD}$content${ConsoleStyle.RESET}"
fun underline(content: String): String = "${ConsoleStyle.UNDERLINE}$content${ConsoleStyle.RESET}"

const val REPLACE_LINE = "\r\u001B[2K"