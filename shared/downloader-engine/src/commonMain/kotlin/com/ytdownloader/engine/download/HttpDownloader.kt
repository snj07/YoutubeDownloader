package com.ytdownloader.engine.download

import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.Outcome
import com.ytdownloader.engine.util.HttpClientProvider
import com.ytdownloader.engine.util.RateLimiter
import io.ktor.client.*
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

internal data class HttpDownloadResult(
    val filePath: String,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val eTag: String?,
    val lastModified: String?
)

internal class HttpDownloader(
    private val client: HttpClient,
    private val controller: DownloadController,
    private val rateLimiter: RateLimiter? = null
) {
    companion object {
        /** Download chunk size: 10 MB (same as yt-dlp: CHUNK_SIZE = 10 << 20). */
        private const val CHUNK_SIZE = 10L * 1024 * 1024  // 10 MB

        /**
         * Proactive URL refresh threshold.
         * YouTube IOS-routed URLs (c=IOS) have a per-URL download budget of ~20MB.
         * Refresh the URL before reaching that limit to avoid 403 errors.
         */
        private const val URL_BUDGET_THRESHOLD = 15L * 1024 * 1024  // 15 MB

        /** Maximum number of URL refresh retries per download. */
        private const val MAX_URL_REFRESHES = 100

        fun extractCdnClient(url: String): String? {
            return Regex("[?&]c=([A-Z_]+)").find(url)?.groupValues?.get(1)
        }
    }

    private val platformDownloader = PlatformChunkDownloader()

    /**
     * Download a file from [url] to [outputPath] with chunked downloading and progress reporting.
     *
     * @param urlRefresher Optional callback to fetch a fresh stream URL when the current one
     *   is exhausted (HTTP 403) or approaching the per-URL download budget. Called with the
     *   current URL, returns a new URL or null if refresh is not possible.
     */
    suspend fun download(
        url: String,
        outputPath: String,
        resumeBytes: Long,
        urlRefresher: (suspend () -> String?)? = null,
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit
    ): Outcome<HttpDownloadResult> {
        try {
            var downloaded = resumeBytes
            var totalBytes: Long? = null
            var eTag: String? = null
            var lastModified: String? = null
            var isFirstChunk = true

            var currentUrl = url
            var bytesWithCurrentUrl = 0L
            var urlRefreshCount = 0

            while (true) {
                if (controller.isCancelled() || !coroutineContext.isActive) break
                controller.awaitIfPaused()

                // Proactive URL refresh: refresh before hitting the ~20MB budget limit
                if (urlRefresher != null && bytesWithCurrentUrl >= URL_BUDGET_THRESHOLD
                    && urlRefreshCount < MAX_URL_REFRESHES) {
                    println("[DEBUG-DL] Proactive URL refresh after ${bytesWithCurrentUrl / 1024 / 1024}MB " +
                            "(refresh #${urlRefreshCount + 1})")
                    val freshUrl = urlRefresher()
                    if (freshUrl != null) {
                        currentUrl = freshUrl
                        bytesWithCurrentUrl = 0L
                        urlRefreshCount++
                        println("[DEBUG-DL] Got fresh URL (c=${extractCdnClient(freshUrl)})")
                    } else {
                        println("[DEBUG-DL] URL refresh returned null, continuing with current URL")
                    }
                }

                val useSingleRequest = false
                val effectiveChunkSize = if (isFirstChunk) CHUNK_SIZE
                    else (CHUNK_SIZE * Random.nextDouble(0.95, 1.0)).toLong()

                val rangeEnd = if (totalBytes != null) {
                    minOf(downloaded + effectiveChunkSize - 1, totalBytes!! - 1)
                } else {
                    downloaded + effectiveChunkSize - 1
                }

                println("[DEBUG-DL] Chunk range=${downloaded}-${rangeEnd}")

                val result = platformDownloader.downloadChunk(
                    url = currentUrl,
                    startByte = downloaded,
                    endByte = rangeEnd,
                    userAgent = HttpClientProvider.ANDROID_VR_UA,
                    outputPath = outputPath,
                    append = !isFirstChunk || resumeBytes > 0
                ) { bytesInChunk ->
                    rateLimiter?.throttle(bytesInChunk.toInt())
                    downloaded += bytesInChunk
                    onProgress(downloaded, totalBytes)
                }

                println("[DEBUG-DL] Result: HTTP ${result.httpStatus} " +
                        "bytes=${result.bytesDownloaded} total=${result.totalContentLength}")

                // Reactive URL refresh: retry on HTTP 403 (URL budget exhausted)
                if (result.httpStatus == 403 && urlRefresher != null
                    && urlRefreshCount < MAX_URL_REFRESHES) {
                    println("[DEBUG-DL] HTTP 403 - URL budget exhausted after " +
                            "${bytesWithCurrentUrl / 1024 / 1024}MB, refreshing URL " +
                            "(refresh #${urlRefreshCount + 1})")
                    val freshUrl = urlRefresher()
                    if (freshUrl != null) {
                        currentUrl = freshUrl
                        bytesWithCurrentUrl = 0L
                        urlRefreshCount++
                        println("[DEBUG-DL] Got fresh URL, retrying chunk")
                        continue  // Retry the same chunk with the fresh URL
                    } else {
                        println("[DEBUG-DL] URL refresh failed, giving up")
                    }
                }

                if (result.error != null || result.httpStatus !in 200..299) {
                    return Outcome.Failure(
                        DownloadError.NetworkFailure(
                            RuntimeException(result.error ?: "HTTP ${result.httpStatus}")
                        )
                    )
                }

                bytesWithCurrentUrl += result.bytesDownloaded

                if (totalBytes == null) {
                    totalBytes = result.totalContentLength
                }
                if (isFirstChunk) {
                    eTag = result.eTag
                    lastModified = result.lastModified
                }

                isFirstChunk = false

                if (useSingleRequest) break
                if (totalBytes != null && downloaded >= totalBytes) break
                if (result.bytesDownloaded < effectiveChunkSize) break
            }

            return if (controller.isCancelled()) {
                Outcome.Failure(DownloadError.Cancelled())
            } else if (downloaded == 0L) {
                Outcome.Failure(DownloadError.NetworkFailure(RuntimeException("Empty response body")))
            } else {
                Outcome.Success(
                    HttpDownloadResult(
                        filePath = outputPath,
                        totalBytes = totalBytes,
                        downloadedBytes = downloaded,
                        eTag = eTag,
                        lastModified = lastModified
                    )
                )
            }
        } catch (t: Throwable) {
            return Outcome.Failure(DownloadError.NetworkFailure(t))
        }
    }

}
