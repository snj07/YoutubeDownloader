package com.ytdownloader.engine

import com.ytdownloader.engine.download.KtorDownloaderEngine
import com.ytdownloader.engine.ffmpeg.FfmpegAudioConverter
import com.ytdownloader.engine.ffmpeg.FfmpegMediaMuxer

actual object EngineFactory {
    actual fun create(config: EngineConfig): DownloaderEngine {
        val ffmpegPath = config.ffmpegPath
        val audioConverter = if (ffmpegPath != null) FfmpegAudioConverter(ffmpegPath) else FfmpegAudioConverter()
        val mediaMuxer = if (ffmpegPath != null) FfmpegMediaMuxer(ffmpegPath) else FfmpegMediaMuxer()
        return KtorDownloaderEngine(audioConverter = audioConverter, mediaMuxer = mediaMuxer)
    }
}
