package com.ytdownloader.engine

expect object EngineFactory {
    fun create(config: EngineConfig = EngineConfig()): DownloaderEngine
}
