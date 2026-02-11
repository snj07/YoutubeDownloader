package com.ytdownloader.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadId(val value: String)

@Serializable
data class DownloadRequest(
    val id: DownloadId,
    val url: String,
    val qualityPreference: QualityPreference,
    val outputFormat: OutputFormat,
    val outputDirectory: String
)

@Serializable
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?
)

sealed class DownloadEvent {
    data class Queued(val id: DownloadId) : DownloadEvent()
    data class Started(val id: DownloadId) : DownloadEvent()
    data class Progress(val id: DownloadId, val progress: DownloadProgress) : DownloadEvent()
    data class Paused(val id: DownloadId) : DownloadEvent()
    data class Resumed(val id: DownloadId) : DownloadEvent()
    data class Completed(val id: DownloadId, val filePath: String) : DownloadEvent()
    data class Failed(val id: DownloadId, val error: DomainError) : DownloadEvent()
    data class Cancelled(val id: DownloadId) : DownloadEvent()
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
data class DownloadTask(
    val id: DownloadId,
    val url: String,
    val status: DownloadStatus,
    val title: String?,
    val outputPath: String?,
    val progress: DownloadProgress?
)
