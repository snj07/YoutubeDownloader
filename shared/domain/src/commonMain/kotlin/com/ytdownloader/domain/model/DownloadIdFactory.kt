package com.ytdownloader.domain.model

import kotlin.random.Random
import kotlin.math.absoluteValue

object DownloadIdFactory {
    fun create(): DownloadId {
        val random = Random.nextInt(1000, 9999)
        val stamp = Random.nextLong().absoluteValue
        return DownloadId("$stamp-$random")
    }
}
