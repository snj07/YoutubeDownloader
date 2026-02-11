package com.ytdownloader.engine.ffmpeg

import com.ytdownloader.domain.model.Outcome

interface AudioConverter {
    suspend fun convertToMp3(inputPath: String, outputPath: String): Outcome<Unit>
}
