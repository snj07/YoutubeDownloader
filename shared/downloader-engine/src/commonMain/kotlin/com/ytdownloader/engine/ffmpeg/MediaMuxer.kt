package com.ytdownloader.engine.ffmpeg

import com.ytdownloader.domain.model.Outcome

interface MediaMuxer {
    suspend fun mux(videoPath: String, audioPath: String, outputPath: String): Outcome<Unit>
}
