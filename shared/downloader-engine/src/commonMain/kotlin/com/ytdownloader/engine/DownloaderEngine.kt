package com.ytdownloader.engine

import com.ytdownloader.domain.model.*
import com.ytdownloader.engine.model.DownloadState
import kotlinx.coroutines.flow.Flow

interface DownloaderEngine {
    suspend fun fetchVideoInfo(url: String): Outcome<VideoInfo>
    fun startDownload(request: DownloadRequest, resumeState: DownloadState?): DownloadSession
}

interface DownloadSession {
    val events: Flow<DownloadEvent>
    suspend fun pause()
    suspend fun resume()
    suspend fun cancel()
}
