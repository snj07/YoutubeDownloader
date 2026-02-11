package com.ytdownloader.engine.ffmpeg

import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.Outcome

class FfmpegMediaMuxer(
    private val ffmpegPath: String = "ffmpeg",
    private val processRunner: ProcessRunner = ProcessRunner()
) : MediaMuxer {
    override suspend fun mux(videoPath: String, audioPath: String, outputPath: String): Outcome<Unit> {
        val command = listOf(
            ffmpegPath,
            "-y",
            "-i", videoPath,
            "-i", audioPath,
            "-c", "copy",
            outputPath
        )
        return try {
            val result = processRunner.run(command)
            if (result.exitCode == 0) Outcome.Success(Unit)
            else Outcome.Failure(DownloadError.ConversionFailed(RuntimeException(result.stderr)))
        } catch (t: Throwable) {
            Outcome.Failure(DownloadError.ConversionFailed(t))
        }
    }
}
