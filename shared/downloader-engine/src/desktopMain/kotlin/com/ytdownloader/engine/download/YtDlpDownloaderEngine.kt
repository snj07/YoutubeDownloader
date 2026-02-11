package com.ytdownloader.engine.download

import com.ytdownloader.domain.model.*
import com.ytdownloader.engine.DownloadSession
import com.ytdownloader.engine.DownloaderEngine
import com.ytdownloader.engine.model.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.roundToLong

class YtDlpDownloaderEngine(
    private val ytDlpPath: String = "yt-dlp",
    private val ffmpegPath: String? = null
) : DownloaderEngine {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchVideoInfo(url: String): Outcome<VideoInfo> {
        return try {
            val output = runCommand(listOf(ytDlpPath, "--no-check-certificates", "--no-playlist", "-J", url))
            val payload = json.parseToJsonElement(output).jsonObject
            val id = payload["id"]?.jsonPrimitive?.content ?: return Outcome.Failure(DownloadError.VideoUnavailable())
            val title = payload["title"]?.jsonPrimitive?.content ?: "Untitled"
            val author = payload["uploader"]?.jsonPrimitive?.content ?: "Unknown"
            val duration = payload["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val thumbnail = payload["thumbnail"]?.jsonPrimitive?.content
            Outcome.Success(
                VideoInfo(
                    id = VideoId(id),
                    title = title,
                    author = author,
                    durationSeconds = duration,
                    streams = emptyList(),
                    audioStreams = emptyList(),
                    thumbnailUrl = thumbnail
                )
            )
        } catch (t: Throwable) {
            Outcome.Failure(DownloadError.NetworkFailure(t))
        }
    }

    override fun startDownload(request: DownloadRequest, resumeState: DownloadState?): DownloadSession {
        val events = MutableSharedFlow<DownloadEvent>(
            replay = 1,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller = DownloadController()
        var processRef: Process? = null

        scope.launch {
            events.emit(DownloadEvent.Queued(request.id))
            events.emit(DownloadEvent.Started(request.id))

            val outputDir = File(request.outputDirectory)
            outputDir.mkdirs()
            val outputTemplate = File(outputDir, "%(title).200s_%(id)s.%(ext)s").absolutePath

            val command = buildCommand(request, outputTemplate)
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            processRef = process

            var outputPath: String? = null

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (controller.isCancelled()) return@forEach
                    parseProgress(line)?.let { progress ->
                        events.tryEmit(DownloadEvent.Progress(request.id, progress))
                    }
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && trimmed.startsWith(outputDir.absolutePath)) {
                        outputPath = trimmed
                    }
                }
            }

            val exitCode = process.waitFor()
            if (controller.isCancelled()) {
                events.emit(DownloadEvent.Cancelled(request.id))
                return@launch
            }

            val resolvedPath = outputPath
            val file = resolvedPath?.let { File(it) }
            if (exitCode == 0 && file != null && file.exists() && file.length() > 0L) {
                events.emit(DownloadEvent.Completed(request.id, file.absolutePath))
            } else {
                val error = DownloadError.NetworkFailure(RuntimeException("yt-dlp failed (exit=$exitCode, bytes=${file?.length() ?: 0})"))
                events.emit(DownloadEvent.Failed(request.id, error))
            }
        }

        return object : DownloadSession {
            override val events: Flow<DownloadEvent> = events.asSharedFlow()

            override suspend fun pause() {
                // yt-dlp does not support pause; no-op to keep UI responsive.
            }

            override suspend fun resume() {
                // yt-dlp does not support resume; no-op to keep UI responsive.
            }

            override suspend fun cancel() {
                controller.cancel()
                processRef?.destroy()
            }
        }
    }

    private fun buildCommand(request: DownloadRequest, outputTemplate: String): List<String> {
        val formatSelector = when (request.qualityPreference) {
            QualityPreference.BEST -> "bestvideo+bestaudio/best"
            QualityPreference.UHD_2160 -> "bestvideo[height<=2160]+bestaudio/best[height<=2160]"
            QualityPreference.QHD_1440 -> "bestvideo[height<=1440]+bestaudio/best[height<=1440]"
            QualityPreference.HD_1080 -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
            QualityPreference.HD_720 -> "bestvideo[height<=720]+bestaudio/best[height<=720]"
            QualityPreference.SD_480 -> "bestvideo[height<=480]+bestaudio/best[height<=480]"
            QualityPreference.SD_360 -> "bestvideo[height<=360]+bestaudio/best[height<=360]"
        }

        val base = mutableListOf(
            ytDlpPath,
            "--no-check-certificates",
            "--no-playlist",
            "--newline",
            "--print",
            "after_move:filepath",
            "-o",
            outputTemplate
        )

        if (ffmpegPath != null) {
            base += listOf("--ffmpeg-location", ffmpegPath)
        }

        when (request.outputFormat) {
            OutputFormat.MP3 -> {
                base += listOf("-x", "--audio-format", "mp3")
            }
            OutputFormat.MP4 -> {
                base += listOf("-f", formatSelector, "--merge-output-format", "mp4")
            }
            OutputFormat.WEBM -> {
                base += listOf("-f", formatSelector, "--merge-output-format", "webm")
            }
        }

        base += request.url
        return base
    }

    private fun parseProgress(line: String): DownloadProgress? {
        if (!line.startsWith("[download]")) return null
        val percentMatch = Regex("([0-9]+\\.?[0-9]*)% of ([^ ]+)").find(line) ?: return null
        val percent = percentMatch.groupValues[1].toDoubleOrNull() ?: return null
        val totalBytes = parseSizeToBytes(percentMatch.groupValues[2])
        val downloadedBytes = if (totalBytes != null) {
            ((percent / 100.0) * totalBytes).roundToLong()
        } else {
            0L
        }
        return DownloadProgress(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = 0L,
            etaSeconds = null
        )
    }

    private fun parseSizeToBytes(raw: String): Long? {
        val match = Regex("([0-9]+\\.?[0-9]*)([KMGTP]iB|B)").find(raw) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        val multiplier = when (unit) {
            "B" -> 1.0
            "KiB" -> 1024.0
            "MiB" -> 1024.0 * 1024.0
            "GiB" -> 1024.0 * 1024.0 * 1024.0
            "TiB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            "PiB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0
            else -> return null
        }
        return (value * multiplier).roundToLong()
    }

    private fun runCommand(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            throw RuntimeException("yt-dlp failed (exit=$exit): $output")
        }
        return output
    }
}
