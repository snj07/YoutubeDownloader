package com.ytdownloader.playlist

import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.Outcome
import com.ytdownloader.domain.model.VideoId

class YouTubePlaylistParser : PlaylistParser {

    /**
     * Multiple strategies to extract video IDs from YouTube playlist HTML.
     * YouTube embeds playlist data inside ytInitialData JSON in the HTML page.
     */
    override fun parsePlaylistIds(html: String): Outcome<List<VideoId>> {
        // Strategy 1: Extract from ytInitialData JSON blob
        val ids = mutableSetOf<String>()

        // Look for "playlistVideoRenderer" objects which contain videoId
        val rendererRegex = Regex(""""playlistVideoRenderer"\s*:\s*\{[^}]*?"videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
        rendererRegex.findAll(html).forEach { ids.add(it.groupValues[1]) }

        // Also look for standalone "videoId":"XXXXXXXXXXX" patterns (broader match)
        if (ids.isEmpty()) {
            val broadRegex = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
            broadRegex.findAll(html).forEach { ids.add(it.groupValues[1]) }
        }

        // Strategy 2: Look for /watch?v= links with list= parameter context
        if (ids.isEmpty()) {
            val watchRegex = Regex("""/watch\?v=([a-zA-Z0-9_-]{11})(?:&|\\u0026)list=""")
            watchRegex.findAll(html).forEach { ids.add(it.groupValues[1]) }
        }

        // Strategy 3: Look in structured JSON - contents array pattern
        if (ids.isEmpty()) {
            val jsonVideoId = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
            jsonVideoId.findAll(html).forEach { ids.add(it.groupValues[1]) }
        }

        if (ids.isEmpty()) {
            return Outcome.Failure(DownloadError.PlaylistPrivate())
        }

        return Outcome.Success(ids.map { VideoId(it) })
    }
}
