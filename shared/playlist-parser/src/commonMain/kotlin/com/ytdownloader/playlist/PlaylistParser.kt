package com.ytdownloader.playlist

import com.ytdownloader.domain.model.Outcome
import com.ytdownloader.domain.model.VideoId

interface PlaylistParser {
    fun parsePlaylistIds(html: String): Outcome<List<VideoId>>
}
