package com.ytdownloader.engine

import com.ytdownloader.engine.download.KtorDownloaderEngine
import com.ytdownloader.engine.download.YtDlpDownloaderEngine
import com.ytdownloader.engine.ffmpeg.FfmpegAudioConverter
import com.ytdownloader.engine.ffmpeg.FfmpegMediaMuxer
import java.io.File

actual object EngineFactory {
    actual fun create(config: EngineConfig): DownloaderEngine {
        return when (config.mode) {
            EngineMode.KTOR -> buildKtor(config)
            EngineMode.YT_DLP -> {
                val ytPath = config.ytDlpPath ?: findYtDlp()
                if (ytPath != null && isYtDlpAvailable(ytPath)) {
                    println("[Engine] Using yt-dlp at: $ytPath")
                    YtDlpDownloaderEngine(ytPath, config.ffmpegPath)
                } else {
                    println("[Engine] yt-dlp not found, falling back to native Ktor engine")
                    buildKtor(config)
                }
            }
        }
    }

    private fun buildKtor(config: EngineConfig): DownloaderEngine {
        val ffmpegPath = config.ffmpegPath
        val audioConverter = if (ffmpegPath != null) FfmpegAudioConverter(ffmpegPath) else FfmpegAudioConverter()
        val mediaMuxer = if (ffmpegPath != null) FfmpegMediaMuxer(ffmpegPath) else FfmpegMediaMuxer()
        return KtorDownloaderEngine(audioConverter = audioConverter, mediaMuxer = mediaMuxer)
    }

    /**
     * Find yt-dlp binary by searching common locations and using 'which'.
     */
    private fun findYtDlp(): String? {
        // Try common names
        for (name in listOf("yt-dlp", "yt-dlp_macos", "yt-dlp_linux")) {
            if (isYtDlpAvailable(name)) return name
        }
        // Try using 'which' to find yt-dlp on PATH
        return runCatching {
            val process = ProcessBuilder("which", "yt-dlp")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && output.isNotEmpty()) {
                output
            } else {
                null
            }
        }.getOrNull()
    }

    private fun isYtDlpAvailable(path: String): Boolean {
        val file = File(path)
        if (file.isAbsolute && !file.exists()) return false
        return runCatching {
            val process = ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        }.getOrDefault(false)
    }
}
