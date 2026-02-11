package com.ytdownloader.engine.util

import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

internal class RateLimiter(private val bytesPerSecond: Long) {
    private var availableBytes = bytesPerSecond
    private var lastMark = TimeSource.Monotonic.markNow()

    suspend fun throttle(bytes: Int) {
        val elapsedSeconds = lastMark.elapsedNow().toDouble(DurationUnit.SECONDS)
        if (elapsedSeconds >= 1) {
            availableBytes = bytesPerSecond
            lastMark = TimeSource.Monotonic.markNow()
        }
        if (bytes > availableBytes) {
            val waitMillis = ((bytes - availableBytes).toDouble() / bytesPerSecond * 1000).toLong()
            delay(max(1, waitMillis))
            availableBytes = bytesPerSecond
            lastMark = TimeSource.Monotonic.markNow()
        } else {
            availableBytes -= bytes
        }
    }
}
