package com.ytdownloader.domain.usecase

import com.ytdownloader.domain.model.*
import com.ytdownloader.domain.repo.*
import kotlinx.coroutines.flow.Flow

class GetVideoInfoUseCase(private val videoRepository: VideoRepository) {
    suspend operator fun invoke(url: String): Outcome<VideoInfo> = videoRepository.fetchVideoInfo(url)
}

class GetPlaylistIdsUseCase(private val playlistRepository: PlaylistRepository) {
    suspend operator fun invoke(url: String): Outcome<List<VideoId>> = playlistRepository.fetchPlaylistIds(url)
}

class DownloadVideoUseCase(private val downloadRepository: DownloadRepository) {
    operator fun invoke(request: DownloadRequest): Flow<DownloadEvent> = downloadRepository.enqueue(request)
}

class PauseDownloadUseCase(private val downloadRepository: DownloadRepository) {
    suspend operator fun invoke(id: DownloadId) = downloadRepository.pause(id)
}

class ResumeDownloadUseCase(private val downloadRepository: DownloadRepository) {
    suspend operator fun invoke(id: DownloadId) = downloadRepository.resume(id)
}

class CancelDownloadUseCase(private val downloadRepository: DownloadRepository) {
    suspend operator fun invoke(id: DownloadId) = downloadRepository.cancel(id)
}

class ObserveDownloadsUseCase(private val downloadRepository: DownloadRepository) {
    operator fun invoke(): Flow<List<DownloadTask>> = downloadRepository.observeTasks()
}

class EnsureConsentUseCase(private val consentRepository: ConsentRepository) {
    suspend fun isGranted(): Boolean = consentRepository.isConsentGranted()
    suspend fun grant() = consentRepository.grantConsent()
}
