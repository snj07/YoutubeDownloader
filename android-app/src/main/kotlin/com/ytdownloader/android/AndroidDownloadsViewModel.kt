package com.ytdownloader.android

import com.ytdownloader.data.AppContainer
import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidDownloadsViewModel(
    private val container: AppContainer,
    private val scope: CoroutineScope
) {
    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _quality = MutableStateFlow(QualityPreference.BEST)
    val quality: StateFlow<QualityPreference> = _quality.asStateFlow()

    private val _format = MutableStateFlow(OutputFormat.MP4)
    val format: StateFlow<OutputFormat> = _format.asStateFlow()

    private val _playlistPreview = MutableStateFlow<List<VideoId>>(emptyList())
    val playlistPreview: StateFlow<List<VideoId>> = _playlistPreview.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _consentGranted = MutableStateFlow(false)
    val consentGranted: StateFlow<Boolean> = _consentGranted.asStateFlow()

    val tasks = container.observeDownloads()

    init {
        scope.launch(Dispatchers.Default) {
            _consentGranted.value = container.consent.isConsentGranted()
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

    fun grantConsent() {
        scope.launch(Dispatchers.Default) {
            container.consent.grantConsent()
            _consentGranted.value = true
        }
    }

    fun previewPlaylist() {
        val playlistUrl = url.value
        scope.launch(Dispatchers.Default) {
            when (val result = container.getPlaylistIds(playlistUrl)) {
                is Outcome.Success -> _playlistPreview.value = result.value
                is Outcome.Failure -> _error.value = result.error.message
            }
        }
    }

    fun downloadSingle(outputDirectory: String) {
        val request = DownloadRequest(
            id = DownloadIdFactory.create(),
            url = url.value,
            qualityPreference = quality.value,
            outputFormat = format.value,
            outputDirectory = outputDirectory
        )
        scope.launch(Dispatchers.Default) {
            container.downloadVideo(request)
        }
    }

    fun downloadPlaylist(outputDirectory: String) {
        val playlistUrl = url.value
        scope.launch(Dispatchers.Default) {
            when (val result = container.getPlaylistIds(playlistUrl)) {
                is Outcome.Success -> {
                    _playlistPreview.value = result.value
                    result.value.forEach { videoId ->
                        val request = DownloadRequest(
                            id = DownloadIdFactory.create(),
                            url = "https://www.youtube.com/watch?v=${videoId.value}",
                            qualityPreference = quality.value,
                            outputFormat = format.value,
                            outputDirectory = outputDirectory
                        )
                        container.downloadVideo(request)
                    }
                }
                is Outcome.Failure -> {
                    _error.value = when (val error = result.error) {
                        is DownloadError.PlaylistPrivate -> "Playlist is private or unavailable"
                        else -> error.message
                    }
                }
            }
        }
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
