package com.ytdownloader.data.repo

import com.ytdownloader.domain.model.Outcome
import com.ytdownloader.domain.model.VideoInfo
import com.ytdownloader.domain.repo.VideoRepository
import com.ytdownloader.engine.DownloaderEngine

class DefaultVideoRepository(
    private val engine: DownloaderEngine
) : VideoRepository {
    override suspend fun fetchVideoInfo(url: String): Outcome<VideoInfo> = engine.fetchVideoInfo(url)
}
