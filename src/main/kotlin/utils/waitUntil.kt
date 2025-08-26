@file:OptIn(ExperimentalTime::class)

package dev.babies.utils

import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Waits until the given condition is true.
 * @param checkInterval the interval between checks
 * @param timeout the maximum time to wait
 * @param condition the condition to wait for
 * @return true if the condition was met before the timeout, false otherwise
 */
suspend fun waitUntil(
    name: String,
    checkInterval: Duration = 100.milliseconds,
    timeout: Duration = 30.seconds,
    logger: Logger = LoggerFactory.getLogger("waitUntil"),
    condition: suspend () -> Boolean,
): Boolean {
    val start = Clock.System.now()
    var remaining = timeout
    while (Clock.System.now() - start < timeout) {
        remaining -= checkInterval
        logger.debug("Waiting for '$name'. ${timeout-remaining}/$timeout")
        if (condition()) return true
        delay(checkInterval)
    }
    return false
}