package com.ytdownloader.data.repo

import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.Outcome
import com.ytdownloader.domain.model.VideoId
import com.ytdownloader.domain.repo.PlaylistRepository
import com.ytdownloader.engine.util.HttpClientProvider
import com.ytdownloader.playlist.PlaylistParser
import io.ktor.client.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*

class DefaultPlaylistRepository(
    private val parser: PlaylistParser,
    private val client: HttpClient = HttpClientProvider.createPageClient(),
    private val innertubeApiKey: String? = null
) : PlaylistRepository {
    override suspend fun fetchPlaylistIds(url: String): Outcome<List<VideoId>> {
        val playlistId = extractPlaylistId(url)
        if (playlistId == null) {
            return Outcome.Failure(DownloadError.InvalidUrl(url))
        }

        // Strategy 1: Try innertube browse API
        try {
            val innertubeIds = fetchViaInnertube(playlistId)
            if (innertubeIds.isNotEmpty()) {
                return Outcome.Success(innertubeIds)
            }
        } catch (_: Exception) { }

        // Strategy 2: Fetch the playlist page and parse HTML
        return try {
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            val html = client.get(playlistUrl).bodyAsText()
            parser.parsePlaylistIds(html)
        } catch (t: Throwable) {
            Outcome.Failure(DownloadError.NetworkFailure(t))
        }
    }

    private fun extractPlaylistId(url: String): String? {
        val regex = Regex("""[?&]list=([a-zA-Z0-9_-]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Use YouTube's innertube browse API to fetch playlist contents.
     * This is more reliable than HTML scraping.
     */
    private suspend fun fetchViaInnertube(playlistId: String): List<VideoId> {
        val apiKey = innertubeApiKey ?: return emptyList()
        val body = """
        {
            "browseId": "VL$playlistId",
            "context": {
                "client": {
                    "clientName": "WEB",
                    "clientVersion": "2.20240813.01.00",
                    "hl": "en",
                    "gl": "US"
                }
            }
        }
        """.trimIndent()

        val response = client.post("https://www.youtube.com/youtubei/v1/browse?key=$apiKey&prettyPrint=false") {
            setBody(TextContent(body, ContentType.Application.Json))
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            header("X-YouTube-Client-Name", "1")
            header("X-YouTube-Client-Version", "2.20240813.01.00")
            header(HttpHeaders.Origin, "https://www.youtube.com")
        }

        if (!response.status.isSuccess()) return emptyList()

        val text = response.bodyAsText()
        // Extract videoId values from the JSON response
        val ids = mutableSetOf<String>()
        val regex = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
        regex.findAll(text).forEach { ids.add(it.groupValues[1]) }
        return ids.map { VideoId(it) }
    }
}

