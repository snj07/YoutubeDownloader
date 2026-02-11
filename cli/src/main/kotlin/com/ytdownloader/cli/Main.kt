package com.ytdownloader.cli

import com.ytdownloader.data.AppContainer
import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.*
import com.ytdownloader.engine.EngineConfig
import com.ytdownloader.engine.EngineMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

fun main(args: Array<String>) = runBlocking {
    val options = CliOptions.parse(args.toList())
    if (options == null) {
        CliOptions.printUsage()
        return@runBlocking
    }

    val appDir = Paths.get(System.getProperty("user.home"), ".ytdownloader").toString()
    val outputDir = options.outputDir
        ?: Paths.get(System.getProperty("user.home"), "Downloads", "YouTubeDownloader").toString()
    val config = EngineConfig(
        mode = options.engineMode,
        ytDlpPath = options.ytDlpPath,
        ffmpegPath = options.ffmpegPath
    )
    val container = AppContainer(appDir, config)

    if (!container.consent.isConsentGranted()) {
        println("You must accept the disclaimer in the desktop app before using the CLI.")
        return@runBlocking
    }

    if (options.isPlaylist) {
        val playlistResult = container.getPlaylistIds(options.url)
        if (playlistResult is Outcome.Failure) {
            println("Failed to parse playlist: ${playlistResult.error.message}")
            return@runBlocking
        }
        val playlistIds = (playlistResult as Outcome.Success).value
        val limitedIds = options.playlistMax?.let { max -> playlistIds.take(max.coerceAtLeast(0)) } ?: playlistIds
        println("Playlist contains ${playlistIds.size} videos; downloading ${limitedIds.size}")
        limitedIds.forEach { id ->
            val request = DownloadRequest(
                id = DownloadIdFactory.create(),
                url = "https://www.youtube.com/watch?v=${id.value}",
                qualityPreference = options.quality,
                outputFormat = options.format,
                outputDirectory = outputDir
            )
            downloadAndWait(container, request)
        }
    } else {
        val request = DownloadRequest(
            id = DownloadIdFactory.create(),
            url = options.url,
            qualityPreference = options.quality,
            outputFormat = options.format,
            outputDirectory = outputDir
        )
        downloadAndWait(container, request)
    }
}

private suspend fun downloadAndWait(container: AppContainer, request: DownloadRequest) {
    val done = CompletableDeferred<Unit>()
    val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
        container.downloadVideo(request).collect { event ->
            when (event) {
                is DownloadEvent.Progress -> {
                    val total = event.progress.totalBytes
                    val percent = if (total != null && total > 0) (event.progress.downloadedBytes * 100 / total) else null
                    if (percent != null) {
                        print("\r${event.id.value}: $percent%")
                    } else {
                        print("\r${event.id.value}: ${event.progress.downloadedBytes} bytes")
                    }
                }
                is DownloadEvent.Completed -> {
                    println("\nCompleted: ${event.filePath}")
                    done.complete(Unit)
                }
                is DownloadEvent.Failed -> {
                    val message = when (val error = event.error) {
                        is DownloadError.FormatNotAvailable -> "Requested format not available"
                        else -> error.message
                    }
                    println("\nFailed: $message")
                    done.complete(Unit)
                }
                is DownloadEvent.Cancelled -> {
                    println("\nCancelled")
                    done.complete(Unit)
                }
                else -> Unit
            }
        }
    }
    done.await()
    job.cancel()
}

private data class CliOptions(
    val url: String,
    val format: OutputFormat,
    val quality: QualityPreference,
    val outputDir: String?,
    val isPlaylist: Boolean,
    val engineMode: EngineMode,
    val ytDlpPath: String?,
    val ffmpegPath: String?,
    val playlistMax: Int?
) {
    companion object {
        fun parse(args: List<String>): CliOptions? {
            if (args.isEmpty()) return null
            var url: String? = null
            var format = OutputFormat.MP4
            var quality = QualityPreference.BEST
            var outputDir: String? = null
            var playlist = false
            var engineMode = EngineMode.KTOR
            var ytDlpPath: String? = null
            var ffmpegPath: String? = null
            var playlistMax: Int? = null

            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--url" -> url = args.getOrNull(++i)
                    "--format" -> format = OutputFormat.valueOf(args.getOrNull(++i)?.uppercase() ?: return null)
                    "--quality" -> quality = QualityPreference.valueOf(args.getOrNull(++i)?.uppercase() ?: return null)
                    "--output" -> outputDir = args.getOrNull(++i)
                    "--playlist" -> playlist = true
                    "--engine" -> {
                        val raw = args.getOrNull(++i)?.uppercase() ?: return null
                        engineMode = when (raw) {
                            "YTDLP", "YT-DLP" -> EngineMode.YT_DLP
                            else -> EngineMode.valueOf(raw)
                        }
                    }
                    "--yt-dlp-path" -> ytDlpPath = args.getOrNull(++i)
                    "--ffmpeg-path" -> ffmpegPath = args.getOrNull(++i)
                    "--playlist-max" -> playlistMax = args.getOrNull(++i)?.toIntOrNull() ?: return null
                    "--help" -> return null
                    else -> if (url == null) url = arg
                }
                i++
            }

            return url?.let {
                CliOptions(
                    url = it,
                    format = format,
                    quality = quality,
                    outputDir = outputDir,
                    isPlaylist = playlist,
                    engineMode = engineMode,
                    ytDlpPath = ytDlpPath,
                    ffmpegPath = ffmpegPath,
                    playlistMax = playlistMax
                )
            }
        }

        fun printUsage() {
            println(
                """
                |Usage:
                |  cli --url <video-or-playlist-url> [--playlist] [--playlist-max <N>] [--format mp4|webm|mp3] [--quality best|uhd_2160|qhd_1440|hd_1080|hd_720|sd_480|sd_360] [--engine ktor|yt_dlp] [--yt-dlp-path <path>] [--ffmpeg-path <path>] [--output <dir>]
                |Examples:
                |  cli --url https://www.youtube.com/watch?v=... --format mp3
                |  cli --url https://www.youtube.com/playlist?list=... --playlist
                |  cli --url https://www.youtube.com/watch?v=... --engine ktor
                """.trimMargin()
            )
        }
    }
}
