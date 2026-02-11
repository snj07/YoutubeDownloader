package com.ytdownloader.engine.parser

import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.*
import kotlinx.serialization.json.*

class YouTubePageParser(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Parse video info from a full YouTube page HTML string.
     */
    fun parseVideoInfo(html: String): Outcome<VideoInfo> {
        val playerResponse = extractPlayerResponse(html) ?: return Outcome.Failure(DownloadError.VideoUnavailable())
        return parsePlayerResponseJson(playerResponse)
    }

    /**
     * Parse video info from a pre-parsed innertube API player response JSON.
     */
    fun parseFromPlayerResponse(playerResponse: JsonObject): Outcome<VideoInfo> {
        return parsePlayerResponseJson(playerResponse)
    }

    private fun parsePlayerResponseJson(playerResponse: JsonObject): Outcome<VideoInfo> {
        return try {
            val videoDetails = playerResponse["videoDetails"]?.jsonObject
                ?: return Outcome.Failure(DownloadError.VideoUnavailable())

            val id = videoDetails.string("videoId") ?: return Outcome.Failure(DownloadError.VideoUnavailable())
            val title = videoDetails.string("title") ?: "Untitled"
            val author = videoDetails.string("author") ?: "Unknown"
            val duration = videoDetails.string("lengthSeconds")?.toLongOrNull() ?: 0L
            val thumbnail = videoDetails["thumbnail"]?.jsonObject
                ?.get("thumbnails")?.jsonArray
                ?.lastOrNull()?.jsonObject
                ?.string("url")

            val streamingData = playerResponse["streamingData"]?.jsonObject
            val formats = streamingData?.get("formats")?.jsonArray ?: JsonArray(emptyList())
            val adaptiveFormats = streamingData?.get("adaptiveFormats")?.jsonArray ?: JsonArray(emptyList())

            val videoStreams = mutableListOf<StreamInfo>()
            val audioStreams = mutableListOf<AudioStreamInfo>()

            (formats + adaptiveFormats).forEach { element ->
                val obj = element.jsonObject
                val mimeType = obj.string("mimeType") ?: return@forEach
                val url = extractUrl(obj) ?: return@forEach
                val itag = obj.int("itag") ?: return@forEach
                val bitrate = obj.long("bitrate") ?: 0L
                val contentLength = obj.long("contentLength")
                val codecs = mimeType.substringAfter("codecs=", "").trim('"')

                if (mimeType.startsWith("audio/")) {
                    audioStreams += AudioStreamInfo(
                        itag = itag,
                        mimeType = mimeType,
                        codecs = codecs,
                        bitrate = bitrate,
                        url = url,
                        contentLength = contentLength
                    )
                } else if (mimeType.startsWith("video/")) {
                    val width = obj.int("width") ?: return@forEach
                    val height = obj.int("height") ?: return@forEach
                    val fps = obj.int("fps") ?: 30
                    val hasAudio = codecs.contains("mp4a") || codecs.contains("opus")
                    videoStreams += StreamInfo(
                        itag = itag,
                        mimeType = mimeType,
                        codecs = codecs,
                        width = width,
                        height = height,
                        fps = fps,
                        bitrate = bitrate,
                        url = url,
                        contentLength = contentLength,
                        hasAudio = hasAudio
                    )
                }
            }

            if (videoStreams.isEmpty() && audioStreams.isEmpty()) {
                return Outcome.Failure(DownloadError.FormatNotAvailable())
            }

            Outcome.Success(
                VideoInfo(
                    id = VideoId(id),
                    title = title,
                    author = author,
                    durationSeconds = duration,
                    streams = videoStreams,
                    audioStreams = audioStreams,
                    thumbnailUrl = thumbnail
                )
            )
        } catch (t: Throwable) {
            Outcome.Failure(DownloadError.Unknown(t))
        }
    }

    private fun extractPlayerResponse(html: String): JsonObject? {
        val marker = "ytInitialPlayerResponse"
        val index = html.indexOf(marker)
        if (index == -1) return null
        val jsonStart = html.indexOf('{', index)
        if (jsonStart == -1) return null
        val jsonEnd = findMatchingBrace(html, jsonStart)
        if (jsonEnd == -1) return null
        val rawJson = html.substring(jsonStart, jsonEnd + 1)
        return json.parseToJsonElement(rawJson).jsonObject
    }

    private fun findMatchingBrace(text: String, startIndex: Int): Int {
        var depth = 0
        var inString = false
        var escape = false
        for (i in startIndex until text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                continue
            }
            when (c) {
                '\\' -> if (inString) escape = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun extractUrl(format: JsonObject): String? {
        format["url"]?.jsonPrimitive?.contentOrNull?.let { return it }
        val cipher = format["signatureCipher"]?.jsonPrimitive?.contentOrNull ?: return null
        val params = cipher.split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size == 2) pair[0] to percentDecode(pair[1]) else null
        }.toMap()
        val url = params["url"] ?: return null
        val s = params["s"]
        if (s == null) return url
        // Ciphered signatures are no longer supported in this engine path
        return null
    }

    private fun percentDecode(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            when {
                value[i] == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 3
                    } else {
                        sb.append(value[i])
                        i++
                    }
                }
                value[i] == '+' -> {
                    sb.append(' ')
                    i++
                }
                else -> {
                    sb.append(value[i])
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
}
