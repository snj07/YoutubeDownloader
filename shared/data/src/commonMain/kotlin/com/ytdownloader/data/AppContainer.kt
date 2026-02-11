package com.ytdownloader.data

import com.ytdownloader.data.consent.FileConsentRepository
import com.ytdownloader.data.download.DefaultDownloadRepository
import com.ytdownloader.data.repo.DefaultPlaylistRepository
import com.ytdownloader.data.repo.DefaultVideoRepository
import com.ytdownloader.data.store.FileDownloadStateStore
import com.ytdownloader.domain.repo.ConsentRepository
import com.ytdownloader.domain.repo.DownloadRepository
import com.ytdownloader.domain.repo.PlaylistRepository
import com.ytdownloader.domain.repo.VideoRepository
import com.ytdownloader.domain.usecase.*
import com.ytdownloader.engine.EngineConfig
import com.ytdownloader.engine.EngineFactory
import com.ytdownloader.playlist.YouTubePlaylistParser

class AppContainer(
    baseDirectory: String,
    engineConfig: EngineConfig = EngineConfig(),
    innertubeApiKey: String? = null
) {
    private val engine = EngineFactory.create(engineConfig)
    private val playlistParser = YouTubePlaylistParser()

    private val stateStore = FileDownloadStateStore("$baseDirectory/downloads")
    private val consentRepo = FileConsentRepository(baseDirectory)

    private val videoRepository: VideoRepository = DefaultVideoRepository(engine)
    private val playlistRepository: PlaylistRepository = DefaultPlaylistRepository(
        playlistParser,
        innertubeApiKey = innertubeApiKey
    )
    private val downloadRepository: DownloadRepository = DefaultDownloadRepository(engine, stateStore)

    val getVideoInfo = GetVideoInfoUseCase(videoRepository)
    val getPlaylistIds = GetPlaylistIdsUseCase(playlistRepository)
    val downloadVideo = DownloadVideoUseCase(downloadRepository)
    val pauseDownload = PauseDownloadUseCase(downloadRepository)
    val resumeDownload = ResumeDownloadUseCase(downloadRepository)
    val cancelDownload = CancelDownloadUseCase(downloadRepository)
    val observeDownloads = ObserveDownloadsUseCase(downloadRepository)

    val consent: ConsentRepository = consentRepo
}
