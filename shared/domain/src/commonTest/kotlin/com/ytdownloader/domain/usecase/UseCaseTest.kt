package com.ytdownloader.domain.usecase

import com.ytdownloader.domain.model.Outcome
import com.ytdownloader.domain.model.VideoId
import com.ytdownloader.domain.model.VideoInfo
import com.ytdownloader.domain.repo.VideoRepository
import kotlin.test.Test
import kotlin.test.assertTrue

class UseCaseTest {
    @Test
    fun getVideoInfoDelegatesToRepository() {
        val repo = object : VideoRepository {
            override suspend fun fetchVideoInfo(url: String): Outcome<VideoInfo> {
                return Outcome.Success(
                    VideoInfo(
                        id = VideoId("abc123xyz00"),
                        title = "Test",
                        author = "Author",
                        durationSeconds = 10,
                        streams = emptyList(),
                        audioStreams = emptyList(),
                        thumbnailUrl = null
                    )
                )
            }
        }
        val useCase = GetVideoInfoUseCase(repo)
        val result = kotlinx.coroutines.runBlocking { useCase("url") }
        assertTrue(result is Outcome.Success)
    }
}
