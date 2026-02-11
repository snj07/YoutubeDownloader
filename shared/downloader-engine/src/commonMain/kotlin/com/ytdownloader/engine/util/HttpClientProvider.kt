package com.ytdownloader.engine.util

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

object HttpClientProvider {
    private const val BROWSER_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** ANDROID_VR (Oculus Quest 3) user agent — used for both innertube API and stream downloads. */
    const val ANDROID_VR_UA =
        "com.google.android.apps.youtube.vr.oculus/1.71.26 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    /**
     * Client for fetching YouTube pages and API requests.
     * Includes consent cookies so YouTube doesn't redirect to a consent page.
     */
    fun createPageClient(): HttpClient {
        return HttpClient {
            install(HttpRedirect)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, BROWSER_UA)
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                header(HttpHeaders.Cookie, "CONSENT=YES+1; SOCS=CAI")
            }
        }
    }

    /**
     * Client for downloading media from YouTube CDN (googlevideo.com).
     *
     * Uses the CIO engine (HTTP/1.1 only, Java NIO+SSL) instead of OkHttp.
     * Each instance should be used for a **single request** and then closed,
     * because YouTube CDN blocks subsequent byte-range requests made on the
     * same keep-alive TCP connection.  The caller creates a fresh client per
     * chunk to force a new connection — the same pattern as separate curl
     * invocations.
     */
    fun createDownloadClient(userAgent: String = ANDROID_VR_UA): HttpClient {
        return HttpClient(CIO) {
            expectSuccess = false
            install(HttpRedirect)
            engine {
                // Disable connection pooling — each client is used once
                maxConnectionsCount = 1
                endpoint {
                    keepAliveTime = 0
                    connectTimeout = 30_000
                    socketTimeout = 120_000
                }
            }
            defaultRequest {
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.AcceptEncoding, "identity")
            }
        }
    }

    /** Backward compat alias */
    fun create(): HttpClient = createPageClient()
}
