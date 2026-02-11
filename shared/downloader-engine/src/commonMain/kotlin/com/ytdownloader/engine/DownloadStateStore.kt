package com.ytdownloader.engine

import com.ytdownloader.domain.model.DownloadId
import com.ytdownloader.engine.model.DownloadState

interface DownloadStateStore {
    suspend fun get(id: DownloadId): DownloadState?
    suspend fun save(state: DownloadState)
    suspend fun delete(id: DownloadId)
}
