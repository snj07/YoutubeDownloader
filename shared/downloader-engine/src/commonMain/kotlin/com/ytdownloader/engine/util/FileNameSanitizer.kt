package com.ytdownloader.engine.util

object FileNameSanitizer {
    private val invalidChars = Regex("[\\\\/:*?\"<>|]")

    fun sanitize(title: String, fallback: String = "video"): String {
        val cleaned = title.replace(invalidChars, "_").trim()
        return if (cleaned.isBlank()) fallback else cleaned
    }
}
