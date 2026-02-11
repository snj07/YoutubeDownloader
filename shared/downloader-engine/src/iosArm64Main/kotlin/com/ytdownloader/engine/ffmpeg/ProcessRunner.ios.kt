package com.ytdownloader.engine.ffmpeg

actual class ProcessRunner {
    actual suspend fun run(command: List<String>): ProcessResult {
        return ProcessResult(127, "", "Process execution not supported on iOS")
    }
}
