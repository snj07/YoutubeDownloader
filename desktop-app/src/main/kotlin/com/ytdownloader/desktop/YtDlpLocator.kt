package com.ytdownloader.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object YtDlpLocator {
    fun resolveBundledYtDlp(): String? {
        val os = System.getProperty("os.name").lowercase()
        val resourceName = when {
            os.contains("mac") -> "yt-dlp-macos"
            os.contains("win") -> "yt-dlp-windows.exe"
            else -> "yt-dlp-linux"
        }

        val resource = YtDlpLocator::class.java.getResource("/yt-dlp/$resourceName") ?: return null
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
}
