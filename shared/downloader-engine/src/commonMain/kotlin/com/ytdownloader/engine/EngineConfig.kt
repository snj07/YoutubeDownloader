package com.ytdownloader.engine

enum class EngineMode {
    KTOR,
    YT_DLP
}

data class EngineConfig(
    val mode: EngineMode = EngineMode.KTOR,
    val ytDlpPath: String? = null,
    val ffmpegPath: String? = null
)
