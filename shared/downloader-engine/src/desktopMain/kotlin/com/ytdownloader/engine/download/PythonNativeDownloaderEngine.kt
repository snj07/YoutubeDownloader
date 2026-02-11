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

class PythonNativeDownloaderEngine(
    private val pythonPath: String = "python3"
) : DownloaderEngine {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchVideoInfo(url: String): Outcome<VideoInfo> {
        return try {
            val output = runCommand(listOf(pythonPath, toolPath(), "info", "--url", url))
            val payload = json.parseToJsonElement(output.trim()).jsonObject
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

            val command = listOf(
                pythonPath,
                toolPath(),
                "download",
                "--url",
                request.url,
                "--output",
                outputDir.absolutePath,
                "--quality",
                request.qualityPreference.name.lowercase(),
                "--format",
                request.outputFormat.name.lowercase()
            )

            val processBuilder = ProcessBuilder(command)
            processBuilder.environment()["PYTHONUNBUFFERED"] = "1"
            val process = processBuilder
                .redirectErrorStream(true)
                .start()
            processRef = process

            var outputPath: String? = null

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (controller.isCancelled()) return@forEach
                    when {
                        line.startsWith("PROGRESS ") -> {
                            val parts = line.trim().split(" ")
                            if (parts.size >= 3) {
                                val downloaded = parts[1].toLongOrNull() ?: 0L
                                val total = parts[2].toLongOrNull().takeIf { it != null && it > 0 }
                                events.tryEmit(
                                    DownloadEvent.Progress(
                                        request.id,
                                        DownloadProgress(downloaded, total, 0L, null)
                                    )
                                )
                            }
                        }
                        line.startsWith("FILEPATH ") -> {
                            outputPath = line.removePrefix("FILEPATH ").trim()
                        }
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
                val error = DownloadError.NetworkFailure(RuntimeException("python downloader failed (exit=$exitCode, bytes=${file?.length() ?: 0})"))
                events.emit(DownloadEvent.Failed(request.id, error))
            }
        }

        return object : DownloadSession {
            override val events: Flow<DownloadEvent> = events.asSharedFlow()

            override suspend fun pause() {
                // Not supported; no-op.
            }

            override suspend fun resume() {
                // Not supported; no-op.
            }

            override suspend fun cancel() {
                controller.cancel()
                processRef?.destroy()
            }
        }
    }

    private fun toolPath(): String {
        var current = File(System.getProperty("user.dir"))
        while (true) {
            val candidate = File(current, "tools/yt_native.py")
            if (candidate.exists()) return candidate.absolutePath
            val parent = current.parentFile ?: break
            current = parent
        }
        return File(System.getProperty("user.dir"), "tools/yt_native.py").absolutePath
    }

    private fun runCommand(command: List<String>): String {
        val processBuilder = ProcessBuilder(command)
        processBuilder.environment()["PYTHONUNBUFFERED"] = "1"
        val process = processBuilder
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            throw RuntimeException("python downloader failed (exit=$exit): $output")
        }
        return output
    }
}
