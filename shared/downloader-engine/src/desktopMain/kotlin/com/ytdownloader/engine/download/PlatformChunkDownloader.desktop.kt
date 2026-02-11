package com.ytdownloader.engine.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Downloads stream chunks using curl via ProcessBuilder.
 *
 * Uses the `&range=start-end` URL query parameter (DASH fragment approach,
 * same as yt-dlp's `build_fragments`) instead of the HTTP `Range` header.
 * YouTube CDN treats URL `&range=` param and HTTP Range header differently:
 * - HTTP Range header: throttled after ~12MB for IOS-routed URLs
 * - URL `&range=` param: throttled after ~20MB for IOS-routed URLs
 * - ANDROID_VR-routed URLs: no throttling for either approach
 *
 * curl is available by default on macOS, Linux, and Windows 10+.
 */
internal actual class PlatformChunkDownloader actual constructor() {

    actual suspend fun downloadChunk(
        url: String,
        startByte: Long,
        endByte: Long,
        userAgent: String,
        outputPath: String,
        append: Boolean,
        onBytesWritten: suspend (bytesInThisChunk: Long) -> Unit
    ): ChunkDownloadResult = withContext(Dispatchers.IO) {
        val headerFile = File.createTempFile("yt-headers-", ".txt")
        try {
            // Append &range=start-end to URL (yt-dlp DASH fragment approach)
            // This avoids the HTTP Range header throttling on YouTube CDN
            val rangeUrl = if (endByte >= 0) {
                "$url&range=$startByte-$endByte"
            } else {
                url
            }

            val cmd = listOf(
                "curl", "-s", "-L",
                "--dump-header", headerFile.absolutePath,
                "-H", "User-Agent: $userAgent",
                "-H", "Accept-Encoding: identity",
                rangeUrl
            )

            val process = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()

            // Stream curl's stdout (response body) directly to the output file.
            // This avoids buffering the entire 10MB chunk in memory.
            val fos = FileOutputStream(outputPath, append)
            val input = process.inputStream
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesWritten = 0L

            fos.use {
                while (coroutineContext.isActive) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    fos.write(buffer, 0, n)
                    bytesWritten += n
                    onBytesWritten(n.toLong())
                    yield()
                }
            }

            val exitCode = process.waitFor()

            // Parse response headers for Content-Range, ETag, etc.
            val headers = headerFile.readText()
            val contentRange = Regex("Content-Range:\\s*([^\r\n]+)", RegexOption.IGNORE_CASE)
                .find(headers)?.groupValues?.get(1)?.trim()
            val totalFromRange = contentRange?.let {
                Regex("""/(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull()
            }
            // With &range= URL param, response is HTTP 200 (no Content-Range).
            // Extract total size from clen= URL parameter as fallback.
            val totalFromClen = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
            val totalSize = totalFromRange ?: totalFromClen

            val eTag = Regex("ETag:\\s*([^\r\n]+)", RegexOption.IGNORE_CASE)
                .find(headers)?.groupValues?.get(1)?.trim()
            val lastModified = Regex("Last-Modified:\\s*([^\r\n]+)", RegexOption.IGNORE_CASE)
                .find(headers)?.groupValues?.get(1)?.trim()

            // Extract HTTP status from the last status line (handles redirects)
            val httpStatus = Regex("HTTP/\\S+\\s+(\\d+)")
                .findAll(headers).lastOrNull()
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: if (exitCode == 0) 200 else 403

            ChunkDownloadResult(
                httpStatus = httpStatus,
                bytesDownloaded = bytesWritten,
                totalContentLength = totalSize,
                eTag = eTag,
                lastModified = lastModified,
                error = if (exitCode != 0) "curl exit code $exitCode (HTTP $httpStatus)" else null
            )
        } catch (e: Throwable) {
            ChunkDownloadResult(
                httpStatus = -1,
                bytesDownloaded = 0,
                totalContentLength = null,
                eTag = null,
                lastModified = null,
                error = e.message ?: e.toString()
            )
        } finally {
            headerFile.delete()
        }
    }
}
