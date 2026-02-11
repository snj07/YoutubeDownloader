package com.ytdownloader.engine.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

actual class ProcessRunner {
    actual suspend fun run(command: List<String>): ProcessResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
        val exitCode = process.waitFor()
        ProcessResult(exitCode, stdout, stderr)
    }
}
