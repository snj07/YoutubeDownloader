package com.ytdownloader.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object FfmpegLocator {
    fun resolveFfmpeg(): String? {
        return resolveBundledFfmpeg() ?: resolveSystemFfmpeg()
    }

    fun resolveBundledFfmpeg(): String? {
        val os = System.getProperty("os.name").lowercase()
        val resourceName = when {
            os.contains("mac") -> "ffmpeg-macos"
            os.contains("win") -> "ffmpeg-windows.exe"
            else -> "ffmpeg-linux"
        }

        val resource = FfmpegLocator::class.java.getResource("/ffmpeg/$resourceName") ?: return null
        val binDir = Paths.get(System.getProperty("user.home"), ".ytdownloader", "bin").toFile()
        if (!binDir.exists()) {
            binDir.mkdirs()
        }
        val target = File(binDir, resourceName)
        if (!target.exists()) {
            resource.openStream().use { input ->
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            target.setExecutable(true)
        }
        return target.absolutePath
    }

    fun resolveSystemFfmpeg(): String? {
        val os = System.getProperty("os.name").lowercase()
        val candidates = when {
            os.contains("mac") -> listOf(
                "/opt/homebrew/bin/ffmpeg",
                "/usr/local/bin/ffmpeg",
                "/usr/bin/ffmpeg"
            )
            os.contains("win") -> listOf(
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe"
            )
            else -> listOf(
                "/usr/bin/ffmpeg",
                "/usr/local/bin/ffmpeg"
            )
        }

        candidates.firstOrNull { isExecutable(it) }?.let { return it }

        val pathEnv = System.getenv("PATH").orEmpty()
        pathEnv.split(File.pathSeparator).forEach { dir ->
            val candidate = File(dir, if (os.contains("win")) "ffmpeg.exe" else "ffmpeg")
            if (candidate.exists() && candidate.canExecute()) return candidate.absolutePath
        }

        return findViaWhich(os)
    }

    private fun isExecutable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }

    private fun findViaWhich(os: String): String? {
        val command = if (os.contains("win")) listOf("where", "ffmpeg") else listOf("which", "ffmpeg")
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && output.isNotEmpty()) {
                output.lineSequence().firstOrNull { it.isNotBlank() }
            } else {
                null
            }
        }.getOrNull()
    }
}
