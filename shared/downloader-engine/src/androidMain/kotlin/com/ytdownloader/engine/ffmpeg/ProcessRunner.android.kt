package com.ytdownloader.engine.ffmpeg

actual class ProcessRunner {
    actual suspend fun run(command: List<String>): ProcessResult = kotlinx.coroutines.withContext(
        kotlinx.coroutines.Dispatchers.IO
    ) {
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        ProcessResult(exitCode, stdout, stderr)
    }
}
