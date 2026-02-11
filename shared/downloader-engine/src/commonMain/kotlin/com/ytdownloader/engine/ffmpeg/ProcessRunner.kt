package com.ytdownloader.engine.ffmpeg

expect class ProcessRunner() {
    suspend fun run(command: List<String>): ProcessResult
}

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)
