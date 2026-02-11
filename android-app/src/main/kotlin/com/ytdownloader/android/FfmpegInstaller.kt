package com.ytdownloader.android

import android.content.Context
import android.os.Build
import java.io.File

object FfmpegInstaller {
    fun ensureBundledFfmpeg(context: Context): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        val assetName = when {
            abi.startsWith("arm64") -> "ffmpeg-arm64-v8a"
            abi.startsWith("x86_64") -> "ffmpeg-x86_64"
            abi.startsWith("armeabi") -> "ffmpeg-armeabi-v7a"
            else -> null
        } ?: return null

        val targetDir = File(context.filesDir, "ffmpeg")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, "ffmpeg")
        if (!targetFile.exists()) {
            try {
                context.assets.open("ffmpeg/$assetName").use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true)
            } catch (_: Throwable) {
                return null
            }
        }
        return targetFile.absolutePath
    }
}
