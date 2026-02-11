package com.ytdownloader.engine.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.coroutines.coroutineContext

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
        try {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as java.security.KeyStore?)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, SecureRandom())

            // Use &range= URL parameter (yt-dlp DASH fragment approach)
            // instead of HTTP Range header to avoid CDN throttling
            val rangeUrl = if (endByte >= 0) {
                "$url&range=$startByte-$endByte"
            } else {
                url
            }

            @Suppress("DEPRECATION")
            val conn = URL(rangeUrl).openConnection() as HttpsURLConnection
            conn.sslSocketFactory = sslContext.socketFactory
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.instanceFollowRedirects = true

            val httpStatus = conn.responseCode

            if (httpStatus !in 200..299) {
                conn.disconnect()
                return@withContext ChunkDownloadResult(
                    httpStatus = httpStatus,
                    bytesDownloaded = 0,
                    totalContentLength = null,
                    eTag = null,
                    lastModified = null,
                    error = "HTTP $httpStatus"
                )
            }

            val contentRange = conn.getHeaderField("Content-Range")
            val totalFromRange = if (contentRange != null) {
                Regex("""/(\d+)""").find(contentRange)?.groupValues?.get(1)?.toLongOrNull()
            } else null
            // With &range= URL param, response is HTTP 200 (no Content-Range).
            // Extract total size from clen= URL parameter as fallback.
            val totalFromClen = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
            val totalContentLength = totalFromRange ?: totalFromClen

            val eTag = conn.getHeaderField("ETag")
            val lastModified = conn.getHeaderField("Last-Modified")

            var bytesWritten = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val inputStream: InputStream = conn.inputStream

            FileOutputStream(outputPath, append).use { fos ->
                inputStream.use { input ->
                    while (coroutineContext.isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        bytesWritten += read
                        onBytesWritten(read.toLong())
                        yield()
                    }
                }
            }

            conn.disconnect()

            ChunkDownloadResult(
                httpStatus = httpStatus,
                bytesDownloaded = bytesWritten,
                totalContentLength = totalContentLength,
                eTag = eTag,
                lastModified = lastModified
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
        }
    }
}
