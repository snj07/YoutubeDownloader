package com.ytdownloader.playlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubePlaylistParserTest {
    @Test
    fun extractsVideoIds() {
        val html = """
            {"videoId":"abc123xyz00"}
            {"videoId":"def456uvw11"}
            {"videoId":"abc123xyz00"}
        """.trimIndent()

        val parser = YouTubePlaylistParser()
        val result = parser.parsePlaylistIds(html)
        assertTrue(result is com.ytdownloader.domain.model.Outcome.Success)
        val ids = (result as com.ytdownloader.domain.model.Outcome.Success).value
        assertEquals(2, ids.size)
        assertEquals("abc123xyz00", ids.first().value)
    }
}
