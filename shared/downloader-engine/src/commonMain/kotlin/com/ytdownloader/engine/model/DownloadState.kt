package com.ytdownloader.engine.model

import com.ytdownloader.domain.model.DownloadId
import com.ytdownloader.domain.model.OutputFormat
import com.ytdownloader.domain.model.QualityPreference
import kotlinx.serialization.Serializable

@Serializable
data class DownloadState(
    val id: DownloadId,
    val url: String,
    val outputDirectory: String,
    val fileName: String,
    val outputFormat: OutputFormat,
    val qualityPreference: QualityPreference,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val eTag: String?,
    val lastModified: String?,
    val updatedAtEpochMillis: Long
)
