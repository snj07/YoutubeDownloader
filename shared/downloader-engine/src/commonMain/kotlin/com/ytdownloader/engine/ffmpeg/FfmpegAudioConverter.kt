package com.ytdownloader.engine.ffmpeg

import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.Outcome

class FfmpegAudioConverter(
    private val ffmpegPath: String = "ffmpeg",
    private val processRunner: ProcessRunner = ProcessRunner()
) : AudioConverter {
    override suspend fun convertToMp3(inputPath: String, outputPath: String): Outcome<Unit> {
        val command = listOf(
            ffmpegPath,
            "-y",
            "-i", inputPath,
            "-vn",
            "-acodec", "libmp3lame",
            "-b:a", "192k",
            outputPath
        )
        return try {
            val result = processRunner.run(command)
            if (result.exitCode == 0) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(DownloadError.ConversionFailed(RuntimeException(result.stderr)))
            }
        } catch (t: Throwable) {
            Outcome.Failure(DownloadError.ConversionFailed(t))
        }
    }
}
