package com.ytdownloader.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoId(val value: String)

@Serializable
data class PlaylistId(val value: String)

@Serializable
data class VideoInfo(
    val id: VideoId,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val streams: List<StreamInfo>,
    val audioStreams: List<AudioStreamInfo>,
    val thumbnailUrl: String?
)

@Serializable
data class StreamInfo(
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Long,
    val url: String,
    val contentLength: Long?,
    val hasAudio: Boolean
)

@Serializable
data class AudioStreamInfo(
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Long,
    val url: String,
    val contentLength: Long?
)

enum class QualityPreference {
    BEST,
    HD_1080,
    HD_720,
    SD_480,
    SD_360
}

enum class OutputFormat {
    MP4,
    WEBM,
    MP3
}
