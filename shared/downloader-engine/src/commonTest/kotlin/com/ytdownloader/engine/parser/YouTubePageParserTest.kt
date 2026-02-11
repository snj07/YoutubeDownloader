package com.ytdownloader.engine.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubePageParserTest {
    @Test
    fun parsesVideoInfoFromHtml() {
        val html = """
            <html><script>
            var ytInitialPlayerResponse = {
              "videoDetails": {
                "videoId": "abc123xyz00",
                "title": "Test Video",
                "author": "Creator",
                "lengthSeconds": "120",
                "thumbnail": {"thumbnails": [{"url": "http://thumb"}]}
              },
              "streamingData": {
                "formats": [
                  {
                    "itag": 22,
                    "mimeType": "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"",
                    "width": 1280,
                    "height": 720,
                    "fps": 30,
                    "bitrate": 900000,
                    "url": "http://video",
                    "contentLength": "1000"
                  }
                ],
                "adaptiveFormats": [
                  {
                    "itag": 140,
                    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                    "bitrate": 128000,
                    "url": "http://audio",
                    "contentLength": "500"
                  }
                ]
              }
            };
            </script></html>
        """.trimIndent()

        val parser = YouTubePageParser()
        val result = parser.parseVideoInfo(html)
        assertTrue(result is com.ytdownloader.domain.model.Outcome.Success)
        val info = (result as com.ytdownloader.domain.model.Outcome.Success).value
        assertEquals("abc123xyz00", info.id.value)
        assertEquals("Test Video", info.title)
        assertEquals(1, info.streams.size)
        assertEquals(1, info.audioStreams.size)
        assertTrue(info.streams.first().hasAudio)
    }
}
