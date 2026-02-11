package com.ytdownloader.engine.download

import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.*
import com.ytdownloader.engine.DownloadSession
import com.ytdownloader.engine.DownloaderEngine
import com.ytdownloader.engine.ffmpeg.AudioConverter
import com.ytdownloader.engine.ffmpeg.FfmpegAudioConverter
import com.ytdownloader.engine.ffmpeg.FfmpegMediaMuxer
import com.ytdownloader.engine.ffmpeg.MediaMuxer
import com.ytdownloader.engine.model.DownloadState
import com.ytdownloader.engine.parser.InnertubeClient
import com.ytdownloader.engine.parser.YouTubePageParser
import com.ytdownloader.engine.stream.StreamSelector
import com.ytdownloader.engine.stream.SelectedStreams
import com.ytdownloader.engine.util.FileNameSanitizer
import com.ytdownloader.engine.util.HttpClientProvider
import com.ytdownloader.engine.util.ProgressCalculator
import com.ytdownloader.engine.util.RateLimiter
import io.ktor.client.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath

class KtorDownloaderEngine(
    private val pageClient: HttpClient = HttpClientProvider.createPageClient(),
    private val downloadClient: HttpClient = HttpClientProvider.createDownloadClient(),
    private val parser: YouTubePageParser = YouTubePageParser(),
    private val streamSelector: StreamSelector = StreamSelector(),
    private val audioConverter: AudioConverter = FfmpegAudioConverter(),
    private val mediaMuxer: MediaMuxer = FfmpegMediaMuxer(),
    private val rateLimitBytesPerSec: Long? = null
) : DownloaderEngine {

    private val innertubeClient = InnertubeClient()
    /** Cached session data — reused across calls to avoid re-fetching the web page. */
    private var cachedSession: InnertubeClient.SessionData? = null

    /**
     * Create a URL refresher lambda for a specific video/itag.
     * When a CDN URL expires or returns 403, this fetches a fresh player response
     * and extracts the new URL for the same itag.
     */
    private fun createUrlRefresher(videoId: String, itag: Int, isAudio: Boolean): suspend () -> String? = {
        try {
            println("[DEBUG] Refreshing URL for videoId=$videoId itag=$itag isAudio=$isAudio")

            // Refresh session if needed
            val session = cachedSession
                ?: innertubeClient.fetchSessionData("https://www.youtube.com/watch?v=$videoId")
                    .also { cachedSession = it }

            if (session != null) {
                val pr = innertubeClient.fetchPlayerResponse(videoId, session)
                if (pr != null) {
                    val result = parser.parseFromPlayerResponse(pr)
                    if (result is Outcome.Success) {
                        val newUrl = if (isAudio) {
                            result.value.audioStreams.find { it.itag == itag }?.url
                        } else {
                            result.value.streams.find { it.itag == itag }?.url
                        }
                        if (newUrl != null) {
                            println("[DEBUG] Refreshed URL: c=${extractCdnClient(newUrl)}")
                        } else {
                            println("[DEBUG] itag=$itag not found in refreshed response")
                        }
                        newUrl
                    } else {
                        println("[DEBUG] URL refresh parse failed: $result")
                        null
                    }
                } else {
                    println("[DEBUG] URL refresh: innertube returned null")
                    null
                }
            } else {
                println("[DEBUG] URL refresh: no session data")
                null
            }
        } catch (e: Exception) {
            println("[DEBUG] URL refresh exception: ${e.message}")
            null
        }
    }

    override suspend fun fetchVideoInfo(url: String): Outcome<VideoInfo> {
        return try {
            val videoId = InnertubeClient.extractVideoId(url)
                ?: return Outcome.Failure(DownloadError.VideoUnavailable())

            // Step 1: Establish session (fetch web page for cookies + visitor data)
            println("[DEBUG] Establishing session for $videoId...")
            val session = innertubeClient.fetchSessionData(url)
            if (session != null) {
                cachedSession = session
            } else {
                println("[DEBUG] Warning: could not establish session, API calls may fail")
            }

            // Step 2: Call ANDROID_VR API with session data
            println("[DEBUG] Fetching ANDROID_VR player response for $videoId")
            val innertubeResponse = try {
                innertubeClient.fetchPlayerResponse(videoId, session)
            } catch (e: Exception) {
                println("[DEBUG] Innertube exception: ${e.message}")
                null
            }

            if (innertubeResponse != null) {
                println("[DEBUG] Innertube response received, parsing...")
                val result = parser.parseFromPlayerResponse(innertubeResponse)
                if (result is Outcome.Success && result.value.streams.isNotEmpty()) {
                    println("[DEBUG] Got ${result.value.streams.size} video streams and ${result.value.audioStreams.size} audio streams")
                    result.value.streams.take(3).forEach { s ->
                        println("[DEBUG]   Video: ${s.width}x${s.height} itag=${s.itag} c=${extractCdnClient(s.url)}")
                    }
                    result.value.audioStreams.take(2).forEach { a ->
                        println("[DEBUG]   Audio: itag=${a.itag} c=${extractCdnClient(a.url)}")
                    }
                    return result
                } else {
                    println("[DEBUG] Innertube parse result: $result")
                }
            } else {
                println("[DEBUG] Innertube returned null")
            }

            // Fallback: parse the web page's embedded player response
            println("[DEBUG] Trying web page fallback...")
            val html = try { pageClient.get(url).bodyAsText() } catch (e: Exception) {
                println("[DEBUG] Page fetch failed: ${e.message}")
                null
            }
            if (html != null) {
                val directResult = parser.parseVideoInfo(html)
                if (directResult is Outcome.Success && directResult.value.streams.isNotEmpty()) {
                    println("[DEBUG] Web page parse succeeded with ${directResult.value.streams.size} streams")
                    return directResult
                }
            }

            println("[DEBUG] All strategies failed, returning VideoUnavailable")
            Outcome.Failure(DownloadError.VideoUnavailable())
        } catch (t: Throwable) {
            println("[DEBUG] Unexpected exception: ${t::class.simpleName}: ${t.message}")
            Outcome.Failure(DownloadError.NetworkFailure(t))
        }
    }

    /**
     * Probe a stream URL to verify it's accessible.
     * Uses HEAD request first, falls back to ranged GET.
     */
    private suspend fun probeStreamUrl(url: String): Boolean {
        return try {
            println("[DEBUG] Probing: ${url.take(100)}...")
            // Use HEAD first (no body download)
            val headResponse = downloadClient.head(url)
            println("[DEBUG] HEAD response: ${headResponse.status}")
            val headOk = headResponse.status.value in 200..299
            if (!headOk) {
                // Some servers reject HEAD; try ranged GET as fallback
                val getResponse = downloadClient.get(url) {
                    header(HttpHeaders.Range, "bytes=0-1")
                }
                println("[DEBUG] GET probe response: ${getResponse.status}")
                val getOk = getResponse.status.value in 200..299

                getOk
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun validateStreams(selected: SelectedStreams, outputFormat: OutputFormat): DomainError? {
        return when (outputFormat) {
            OutputFormat.MP3 -> {
                val audio = selected.audio ?: return DownloadError.FormatNotAvailable()
                if (!probeStreamUrl(audio.url)) DownloadError.Throttled() else null
            }
            OutputFormat.MP4, OutputFormat.WEBM -> {
                if (selected.requiresMuxing) {
                    val video = selected.video ?: return DownloadError.FormatNotAvailable()
                    val audio = selected.audio ?: return DownloadError.FormatNotAvailable()
                    val videoOk = probeStreamUrl(video.url)
                    val audioOk = probeStreamUrl(audio.url)
                    if (!videoOk || !audioOk) DownloadError.Throttled() else null
                } else {
                    val video = selected.video ?: return DownloadError.FormatNotAvailable()
                    if (!probeStreamUrl(video.url)) DownloadError.Throttled() else null
                }
            }
        }
    }

    override fun startDownload(request: DownloadRequest, resumeState: DownloadState?): DownloadSession {
        val controller = DownloadController()
        val events = MutableSharedFlow<DownloadEvent>(replay = 1, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launch {
            events.emit(DownloadEvent.Queued(request.id))
            events.emit(DownloadEvent.Started(request.id))

            val videoInfoOutcome = fetchVideoInfo(request.url)
            if (videoInfoOutcome is Outcome.Failure) {
                events.emit(DownloadEvent.Failed(request.id, videoInfoOutcome.error))
                return@launch
            }
            var videoInfo = (videoInfoOutcome as Outcome.Success).value
            var videoId = videoInfo.id.value

            var selected = streamSelector.select(videoInfo, request.qualityPreference, request.outputFormat)
            if (selected == null) {
                events.emit(DownloadEvent.Failed(request.id, DownloadError.FormatNotAvailable()))
                return@launch
            }
            var validationError = validateStreams(selected, request.outputFormat)
            if (validationError != null) {
                // Try re-fetching with a fresh session
                println("[DEBUG] Stream validation failed, retrying with fresh session...")
                val freshSession = innertubeClient.fetchSessionData(request.url)
                if (freshSession != null) {
                    cachedSession = freshSession
                    val freshPr = innertubeClient.fetchPlayerResponse(videoId, freshSession)
                    if (freshPr != null) {
                        val freshResult = parser.parseFromPlayerResponse(freshPr)
                        if (freshResult is Outcome.Success && freshResult.value.streams.isNotEmpty()) {
                            videoInfo = freshResult.value
                            videoId = freshResult.value.id.value
                            val freshSelected = streamSelector.select(freshResult.value, request.qualityPreference, request.outputFormat)
                            if (freshSelected != null) {
                                val freshValidation = validateStreams(freshSelected, request.outputFormat)
                                if (freshValidation == null) {
                                    selected = freshSelected
                                    validationError = null
                                }
                            }
                        }
                    }
                }
            }
            if (validationError != null) {
                events.emit(DownloadEvent.Failed(request.id, validationError))
                return@launch
            }

            val rawTitle = videoInfo.title
            val safeTitle = if (rawTitle.contains("http", ignoreCase = true) || rawTitle.contains("youtube.com", ignoreCase = true)) {
                "video_${videoInfo.id.value}"
            } else {
                rawTitle
            }
            val baseName = FileNameSanitizer.sanitize(safeTitle, "video_${request.id.value}")
            val outputDir = request.outputDirectory.toPath()
            val fileSystem = FileSystem.SYSTEM
            fileSystem.createDirectories(outputDir)

            when (request.outputFormat) {
                OutputFormat.MP3 -> {
                    val selectedAudio = selected.audio ?: run {
                        events.emit(DownloadEvent.Failed(request.id, DownloadError.FormatNotAvailable()))
                        return@launch
                    }
                    val tempAudioPath = outputDir.resolve("${baseName}_audio.tmp")
                    val mp3Path = outputDir.resolve("$baseName.mp3")
                    val progressCalculator = ProgressCalculator()

                    val limiter = rateLimitBytesPerSec?.let { RateLimiter(it) }
                    val downloadOutcome = HttpDownloader(downloadClient, controller, limiter).download(
                        url = selectedAudio.url,
                        outputPath = tempAudioPath.toString(),
                        resumeBytes = resumeState?.downloadedBytes ?: 0L,
                        urlRefresher = createUrlRefresher(videoId, selectedAudio.itag, isAudio = true)
                    ) { downloaded, total ->
                        val speed = progressCalculator.update(downloaded)
                        val eta = if (speed > 0 && total != null) ((total - downloaded) / speed) else null
                        events.emit(
                            DownloadEvent.Progress(
                                request.id,
                                DownloadProgress(downloaded, total, speed, eta)
                            )
                        )
                    }

                    if (downloadOutcome is Outcome.Failure) {
                        if (downloadOutcome.error is DownloadError.Cancelled) {
                            events.emit(DownloadEvent.Cancelled(request.id))
                        } else {
                            events.emit(DownloadEvent.Failed(request.id, downloadOutcome.error))
                        }
                        return@launch
                    }

                    val conversion = audioConverter.convertToMp3(tempAudioPath.toString(), mp3Path.toString())
                    if (conversion is Outcome.Failure) {
                        events.emit(DownloadEvent.Failed(request.id, conversion.error))
                        return@launch
                    }
                    fileSystem.delete(tempAudioPath, mustExist = false)
                    events.emit(DownloadEvent.Completed(request.id, mp3Path.toString()))
                }
                OutputFormat.MP4, OutputFormat.WEBM -> {
                    if (selected.requiresMuxing) {
                        val selectedVideo = selected.video ?: return@launch
                        val selectedAudio = selected.audio ?: return@launch
                        val videoPath = outputDir.resolve("${baseName}_video.tmp")
                        val audioPath = outputDir.resolve("${baseName}_audio.tmp")
                        val targetPath = outputDir.resolve("$baseName.${request.outputFormat.name.lowercase()}")

                        val videoProgress = ProgressCalculator()
                        val audioProgress = ProgressCalculator()

                        val videoLimiter = rateLimitBytesPerSec?.let { RateLimiter(it) }
                        val videoOutcome = HttpDownloader(downloadClient, controller, videoLimiter).download(
                            url = selectedVideo.url,
                            outputPath = videoPath.toString(),
                            resumeBytes = resumeState?.downloadedBytes ?: 0L,
                            urlRefresher = createUrlRefresher(videoId, selectedVideo.itag, isAudio = false)
                        ) { downloaded, total ->
                            val speed = videoProgress.update(downloaded)
                            val eta = if (speed > 0 && total != null) ((total - downloaded) / speed) else null
                            events.emit(DownloadEvent.Progress(request.id, DownloadProgress(downloaded, total, speed, eta)))
                        }

                        if (videoOutcome is Outcome.Failure) {
                            if (videoOutcome.error is DownloadError.Cancelled) {
                                events.emit(DownloadEvent.Cancelled(request.id))
                            } else {
                                events.emit(DownloadEvent.Failed(request.id, videoOutcome.error))
                            }
                            return@launch
                        }

                        val audioLimiter = rateLimitBytesPerSec?.let { RateLimiter(it) }
                        val audioOutcome = HttpDownloader(downloadClient, controller, audioLimiter).download(
                            url = selectedAudio.url,
                            outputPath = audioPath.toString(),
                            resumeBytes = 0L,
                            urlRefresher = createUrlRefresher(videoId, selectedAudio.itag, isAudio = true)
                        ) { downloaded, total ->
                            val speed = audioProgress.update(downloaded)
                            val eta = if (speed > 0 && total != null) ((total - downloaded) / speed) else null
                            events.emit(DownloadEvent.Progress(request.id, DownloadProgress(downloaded, total, speed, eta)))
                        }

                        if (audioOutcome is Outcome.Failure) {
                            if (audioOutcome.error is DownloadError.Cancelled) {
                                events.emit(DownloadEvent.Cancelled(request.id))
                            } else {
                                events.emit(DownloadEvent.Failed(request.id, audioOutcome.error))
                            }
                            return@launch
                        }

                        val muxResult = mediaMuxer.mux(videoPath.toString(), audioPath.toString(), targetPath.toString())
                        if (muxResult is Outcome.Failure) {
                            events.emit(DownloadEvent.Failed(request.id, muxResult.error))
                            return@launch
                        }
                        fileSystem.delete(videoPath, mustExist = false)
                        fileSystem.delete(audioPath, mustExist = false)
                        events.emit(DownloadEvent.Completed(request.id, targetPath.toString()))
                    } else {
                        val selectedVideo = selected.video ?: return@launch
                        val targetPath = outputDir.resolve("$baseName.${request.outputFormat.name.lowercase()}")
                        val progressCalculator = ProgressCalculator()

                        val limiter = rateLimitBytesPerSec?.let { RateLimiter(it) }
                        val downloadOutcome = HttpDownloader(downloadClient, controller, limiter).download(
                            url = selectedVideo.url,
                            outputPath = targetPath.toString(),
                            resumeBytes = resumeState?.downloadedBytes ?: 0L,
                            urlRefresher = createUrlRefresher(videoId, selectedVideo.itag, isAudio = false)
                        ) { downloaded, total ->
                            val speed = progressCalculator.update(downloaded)
                            val eta = if (speed > 0 && total != null) ((total - downloaded) / speed) else null
                            events.emit(DownloadEvent.Progress(request.id, DownloadProgress(downloaded, total, speed, eta)))
                        }

                        if (downloadOutcome is Outcome.Failure) {
                            if (downloadOutcome.error is DownloadError.Cancelled) {
                                events.emit(DownloadEvent.Cancelled(request.id))
                            } else {
                                events.emit(DownloadEvent.Failed(request.id, downloadOutcome.error))
                            }
                            return@launch
                        }
                        events.emit(DownloadEvent.Completed(request.id, targetPath.toString()))
                    }
                }
            }
        }

        return object : DownloadSession {
            override val events: Flow<DownloadEvent> = events.asSharedFlow()

            override suspend fun pause() {
                controller.pause()
                events.emit(DownloadEvent.Paused(request.id))
            }

            override suspend fun resume() {
                controller.resume()
                events.emit(DownloadEvent.Resumed(request.id))
            }

            override suspend fun cancel() {
                controller.cancel()
                events.emit(DownloadEvent.Cancelled(request.id))
            }
        }
    }

    companion object {
        /**
         * Extract the `c=` (client) parameter from a YouTube CDN URL.
         */
        internal fun extractCdnClient(url: String): String? {
            return Regex("[?&]c=([A-Z_]+)").find(url)?.groupValues?.get(1)
        }
    }
}
