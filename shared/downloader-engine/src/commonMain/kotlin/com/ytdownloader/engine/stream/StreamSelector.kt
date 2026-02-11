package com.ytdownloader.engine.stream

import com.ytdownloader.domain.model.*

class StreamSelector {
    fun select(videoInfo: VideoInfo, preference: QualityPreference, outputFormat: OutputFormat): SelectedStreams? {
        return when (outputFormat) {
            OutputFormat.MP3 -> {
                val audio = videoInfo.audioStreams.maxByOrNull { it.bitrate } ?: return null
                SelectedStreams(video = null, audio = audio, requiresMuxing = false)
            }
            OutputFormat.MP4, OutputFormat.WEBM -> {
                val progressiveCandidates = videoInfo.streams.filter { it.hasAudio }
                val progressiveMatch = progressiveCandidates
                    .sortedByDescending { it.height }
                    .firstOrNull { matchesPreference(it, preference) }

                val videoOnlyMatch = selectVideoOnly(videoInfo.streams, preference)

                // Pick video-only stream if it provides higher resolution than progressive.
                // For BEST: always prefer highest available resolution (video-only + mux if better).
                // For specific qualities: prefer video-only when it better matches the preference.
                val selectedVideoOnly = if (videoOnlyMatch != null) {
                    val progressiveHeight = progressiveMatch?.height ?: 0
                    if (videoOnlyMatch.height > progressiveHeight) videoOnlyMatch else null
                } else {
                    null
                }

                if (selectedVideoOnly != null) {
                    val audio = videoInfo.audioStreams.maxByOrNull { it.bitrate } ?: return null
                    return SelectedStreams(video = selectedVideoOnly, audio = audio, requiresMuxing = true)
                }

                val progressiveFallback = progressiveMatch
                    ?: progressiveCandidates.maxByOrNull { it.height }

                if (progressiveFallback != null) {
                    return SelectedStreams(video = progressiveFallback, audio = null, requiresMuxing = false)
                }

                val video = videoOnlyMatch
                val audio = videoInfo.audioStreams.maxByOrNull { it.bitrate }
                if (video == null || audio == null) return null
                SelectedStreams(video = video, audio = audio, requiresMuxing = true)
            }
        }
    }

    private fun matchesPreference(stream: StreamInfo, preference: QualityPreference): Boolean {
        return when (preference) {
            QualityPreference.BEST -> true
            QualityPreference.HD_1080 -> stream.height >= 1080
            QualityPreference.HD_720 -> stream.height in 720..1079
            QualityPreference.SD_480 -> stream.height in 480..719
            QualityPreference.SD_360 -> stream.height in 360..479
        }
    }

    private fun selectVideoOnly(streams: List<StreamInfo>, preference: QualityPreference): StreamInfo? {
        val videoOnly = streams.filter { !it.hasAudio }
        return videoOnly.sortedByDescending { it.height }
            .firstOrNull { matchesPreference(it, preference) }
            ?: videoOnly.maxByOrNull { it.height }
    }
}

data class SelectedStreams(
    val video: StreamInfo?,
    val audio: AudioStreamInfo?,
    val requiresMuxing: Boolean
)
