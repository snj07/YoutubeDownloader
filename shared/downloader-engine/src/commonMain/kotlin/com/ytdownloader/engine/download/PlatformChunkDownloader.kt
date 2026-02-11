package com.ytdownloader.engine.download

/**
 * Platform-specific HTTP chunk downloader.
 * Each platform implements this to download a byte range from a URL.
 * On JVM (desktop/Android), uses HttpURLConnection with a fresh SSLContext
 * per request to avoid the JVM-wide TLS session cache that causes YouTube
 * CDN to block subsequent range requests with 403 Forbidden.
 */
internal expect class PlatformChunkDownloader() {
    /**
     * Download a byte range from [url] and write to [outputPath].
     * If [append] is true, appends to an existing file; otherwise creates new.
     * Returns null on success (writes data and reports progress), or an error message.
     */
    suspend fun downloadChunk(
        url: String,
        startByte: Long,
        endByte: Long,
        userAgent: String,
        outputPath: String,
        append: Boolean,
        onBytesWritten: suspend (bytesInThisChunk: Long) -> Unit
    ): ChunkDownloadResult
}

internal data class ChunkDownloadResult(
    val httpStatus: Int,
    val bytesDownloaded: Long,
    val totalContentLength: Long?,
    val eTag: String?,
    val lastModified: String?,
    val error: String? = null
)
