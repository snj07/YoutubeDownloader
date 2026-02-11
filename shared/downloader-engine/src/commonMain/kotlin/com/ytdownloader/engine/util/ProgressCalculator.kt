package com.ytdownloader.engine.util

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

internal class ProgressCalculator {
    private var lastBytes = 0L
    private var lastMark = TimeSource.Monotonic.markNow()

    fun update(downloaded: Long): Long {
        val elapsed: Duration = lastMark.elapsedNow()
        return if (elapsed.inWholeMilliseconds >= 500) {
            val deltaBytes = downloaded - lastBytes
            val speed = (deltaBytes / elapsed.toDouble(DurationUnit.SECONDS)).toLong().coerceAtLeast(0L)
            lastBytes = downloaded
            lastMark = TimeSource.Monotonic.markNow()
            speed
        } else {
            0L
        }
    }
}
