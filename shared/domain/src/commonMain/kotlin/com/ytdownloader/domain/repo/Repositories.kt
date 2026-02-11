package com.ytdownloader.domain.repo

import com.ytdownloader.domain.model.*
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    suspend fun fetchVideoInfo(url: String): Outcome<VideoInfo>
}

interface PlaylistRepository {
    suspend fun fetchPlaylistIds(url: String): Outcome<List<VideoId>>
}

interface DownloadRepository {
    fun enqueue(request: DownloadRequest): Flow<DownloadEvent>
    suspend fun pause(id: DownloadId)
    suspend fun resume(id: DownloadId)
    suspend fun cancel(id: DownloadId)
    fun observeTasks(): Flow<List<DownloadTask>>
}

interface ConsentRepository {
    suspend fun isConsentGranted(): Boolean
    suspend fun grantConsent()
}
