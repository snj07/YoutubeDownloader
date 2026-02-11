package com.ytdownloader.desktop

import com.ytdownloader.data.AppContainer
import com.ytdownloader.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class DownloadsViewModel(
    private val container: AppContainer,
    private val scope: CoroutineScope
) {
    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _quality = MutableStateFlow(QualityPreference.BEST)
    val quality: StateFlow<QualityPreference> = _quality.asStateFlow()

    private val _format = MutableStateFlow(OutputFormat.MP4)
    val format: StateFlow<OutputFormat> = _format.asStateFlow()

    data class PlaylistItemUi(
        val id: VideoId,
        val title: String,
        val thumbnailUrl: String?,
        val selected: Boolean,
        val downloadId: DownloadId? = null,
        val status: DownloadStatus? = null,
        val progress: DownloadProgress? = null,
        val outputPath: String? = null
    )

    data class SingleVideoUi(
        val id: DownloadId,
        val title: String,
        val thumbnailUrl: String?,
        val status: DownloadStatus = DownloadStatus.QUEUED,
        val progress: DownloadProgress? = null,
        val outputPath: String? = null,
        val errorMessage: String? = null
    )

    private val _playlistItems = MutableStateFlow<List<PlaylistItemUi>>(emptyList())
    val playlistItems: StateFlow<List<PlaylistItemUi>> = _playlistItems.asStateFlow()

    private val _playlistLoading = MutableStateFlow(false)
    val playlistLoading: StateFlow<Boolean> = _playlistLoading.asStateFlow()

    private val _singleVideo = MutableStateFlow<SingleVideoUi?>(null)
    val singleVideo: StateFlow<SingleVideoUi?> = _singleVideo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _consentGranted = MutableStateFlow(false)
    val consentGranted: StateFlow<Boolean> = _consentGranted.asStateFlow()
    private val _consentLoaded = MutableStateFlow(false)
    val consentLoaded: StateFlow<Boolean> = _consentLoaded.asStateFlow()

    private val activeJobs = mutableListOf<Job>()

    init {
        scope.launch(Dispatchers.Default) {
            val granted = runCatching { container.consent.isConsentGranted() }.getOrDefault(false)
            _consentGranted.value = granted
            _consentLoaded.value = true
        }
    }

    fun updateUrl(value: String) {
        _url.value = value
    }

    fun updateQuality(value: QualityPreference) {
        _quality.value = value
    }

    fun updateFormat(value: OutputFormat) {
        _format.value = value
    }

    fun clearError() {
        _error.value = null
    }

    fun grantConsent() {
        scope.launch(Dispatchers.Default) {
            container.consent.grantConsent()
            _consentGranted.value = true
            _consentLoaded.value = true
        }
    }

    fun reset() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        _url.value = ""
        _error.value = null
        _playlistItems.value = emptyList()
        _playlistLoading.value = false
        _singleVideo.value = null
    }

    fun stopDownload() {
        _singleVideo.value?.id?.let { id ->
            cancelDownload(id)
        }
        _playlistItems.value
            .filter { item ->
                item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED
            }
            .forEach { item -> item.downloadId?.let { id -> cancelDownload(id) } }
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        val current = _singleVideo.value
        if (current != null && current.status == DownloadStatus.DOWNLOADING) {
            _singleVideo.value = current.copy(status = DownloadStatus.CANCELLED)
        }
        _playlistItems.value = _playlistItems.value.map { item ->
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
                item.copy(status = DownloadStatus.CANCELLED)
            } else item
        }
    }

    fun previewPlaylist() {
        val playlistUrl = url.value
        val job = scope.launch(Dispatchers.Default) {
            _playlistLoading.value = true
            _error.value = null
            try {
                when (val result = container.getPlaylistIds(playlistUrl)) {
                    is Outcome.Success -> {
                        val ids = result.value
                        _playlistItems.value = ids.map { id ->
                            PlaylistItemUi(
                                id = id,
                                title = "Loading... ${id.value}",
                                thumbnailUrl = null,
                                selected = true
                            )
                        }
                        val items = supervisorScope {
                            ids.map { id ->
                                async {
                                    val infoResult = container.getVideoInfo("https://www.youtube.com/watch?v=${id.value}")
                                    val info = (infoResult as? Outcome.Success)?.value
                                    PlaylistItemUi(
                                        id = id,
                                        title = info?.title ?: "Video ${id.value}",
                                        thumbnailUrl = info?.thumbnailUrl,
                                        selected = true
                                    )
                                }
                            }.awaitAll()
                        }
                        _playlistItems.value = items
                    }
                    is Outcome.Failure -> _error.value = result.error.message
                }
            } finally {
                _playlistLoading.value = false
            }
        }
        activeJobs.add(job)
    }

    fun downloadSingle(outputDirectory: String) {
        val videoUrl = url.value
        if (videoUrl.isBlank()) {
            _error.value = "Enter a video URL"
            return
        }
        _error.value = null
        val requestId = DownloadIdFactory.create()
        _singleVideo.value = SingleVideoUi(id = requestId, title = "Fetching...", thumbnailUrl = null)

        val request = DownloadRequest(
            id = requestId,
            url = videoUrl,
            qualityPreference = quality.value,
            outputFormat = format.value,
            outputDirectory = outputDirectory
        )
        val job = scope.launch(Dispatchers.Default) {
            val infoResult = container.getVideoInfo(videoUrl)
            val info = (infoResult as? Outcome.Success)?.value
            _singleVideo.value = SingleVideoUi(
                id = requestId,
                title = info?.title ?: videoUrl,
                thumbnailUrl = info?.thumbnailUrl,
                status = DownloadStatus.DOWNLOADING
            )

            container.downloadVideo(request).collect { event ->
                when (event) {
                    is DownloadEvent.Progress -> {
                        _singleVideo.value = _singleVideo.value?.copy(
                            status = DownloadStatus.DOWNLOADING,
                            progress = event.progress
                        )
                    }
                    is DownloadEvent.Completed -> {
                        _singleVideo.value = _singleVideo.value?.copy(
                            status = DownloadStatus.COMPLETED,
                            outputPath = event.filePath
                        )
                    }
                    is DownloadEvent.Failed -> {
                        _singleVideo.value = _singleVideo.value?.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = event.error.message
                        )
                    }
                    is DownloadEvent.Cancelled -> {
                        _singleVideo.value = _singleVideo.value?.copy(
                            status = DownloadStatus.CANCELLED
                        )
                    }
                    else -> {}
                }
            }
        }
        activeJobs.add(job)
    }

    fun downloadPlaylist(outputDirectory: String) {
        val selectedItems = _playlistItems.value.filter { it.selected }
        if (selectedItems.isEmpty()) {
            _error.value = "Select at least one video"
            return
        }
        _error.value = null
        launchPlaylistDownloads(selectedItems, outputDirectory)
    }

    fun downloadAllPlaylist(outputDirectory: String) {
        val items = _playlistItems.value
        if (items.isEmpty()) {
            _error.value = "Preview a playlist first"
            return
        }
        _error.value = null
        launchPlaylistDownloads(items, outputDirectory)
    }

    private fun launchPlaylistDownloads(items: List<PlaylistItemUi>, outputDirectory: String) {
        items.forEach { item ->
            val requestId = DownloadIdFactory.create()
            updatePlaylistItemStatus(item.id, DownloadStatus.QUEUED, null, null, requestId)

            val request = DownloadRequest(
                id = requestId,
                url = "https://www.youtube.com/watch?v=${item.id.value}",
                qualityPreference = quality.value,
                outputFormat = format.value,
                outputDirectory = outputDirectory
            )
            val job = scope.launch(Dispatchers.Default) {
                container.downloadVideo(request).collect { event ->
                    when (event) {
                        is DownloadEvent.Started -> {
                            updatePlaylistItemStatus(item.id, DownloadStatus.DOWNLOADING, null, null, requestId)
                        }
                        is DownloadEvent.Progress -> {
                            updatePlaylistItemStatus(item.id, DownloadStatus.DOWNLOADING, event.progress, null, requestId)
                        }
                        is DownloadEvent.Completed -> {
                            updatePlaylistItemStatus(item.id, DownloadStatus.COMPLETED, null, event.filePath, requestId)
                        }
                        is DownloadEvent.Failed -> {
                            updatePlaylistItemStatus(item.id, DownloadStatus.FAILED, null, null, requestId)
                        }
                        is DownloadEvent.Cancelled -> {
                            updatePlaylistItemStatus(item.id, DownloadStatus.CANCELLED, null, null, requestId)
                        }
                        else -> {}
                    }
                }
            }
            activeJobs.add(job)
        }
    }

    private fun updatePlaylistItemStatus(
        id: VideoId,
        status: DownloadStatus,
        progress: DownloadProgress?,
        outputPath: String?,
        downloadId: DownloadId? = null
    ) {
        _playlistItems.value = _playlistItems.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = status,
                    downloadId = downloadId ?: item.downloadId,
                    progress = progress ?: if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED) null else item.progress,
                    outputPath = outputPath ?: item.outputPath
                )
            } else item
        }
    }

    fun setPlaylistItemSelected(id: VideoId, selected: Boolean) {
        _playlistItems.value = _playlistItems.value.map { item ->
            if (item.id == id) item.copy(selected = selected) else item
        }
    }

    fun selectAllPlaylistItems(selected: Boolean) {
        _playlistItems.value = _playlistItems.value.map { it.copy(selected = selected) }
    }

    fun pauseDownload(id: DownloadId) {
        scope.launch { container.pauseDownload(id) }
    }

    fun resumeDownload(id: DownloadId) {
        scope.launch { container.resumeDownload(id) }
    }

    fun cancelDownload(id: DownloadId) {
        scope.launch { container.cancelDownload(id) }
    }
}
