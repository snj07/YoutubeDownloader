package com.ytdownloader.engine.parser

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.*

/**
 * Fetches video info using YouTube's Innertube API.
 *
 * Strategy (matches yt-dlp behaviour):
 *   1. Fetch the YouTube watch page with a browser User-Agent.
 *      This establishes session cookies and provides VISITOR_DATA needed by the API.
 *   2. Call the /youtubei/v1/player endpoint using the ANDROID_VR client
 *      (Oculus Quest 3, clientNameId=28).
 *      Pass the VISITOR_DATA in the X-Goog-Visitor-Id header and forward cookies.
 *   3. The response contains streamingData with direct, ANDROID_VR-routed URLs
 *      that do NOT require PO tokens, signature deciphering, or n-parameter transforms.
 */
class InnertubeClient(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object {
        private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"

        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        const val ANDROID_VR_UA =
            "com.google.android.apps.youtube.vr.oculus/1.71.26 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

        private val VISITOR_DATA_RE = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")

        fun extractVideoId(url: String): String? {
            val patterns = listOf(
                Regex("""[?&]v=([a-zA-Z0-9_-]{11})"""),
                Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
                Regex("""/embed/([a-zA-Z0-9_-]{11})"""),
                Regex("""/shorts/([a-zA-Z0-9_-]{11})"""),
                Regex("""/v/([a-zA-Z0-9_-]{11})"""),
                Regex("""^([a-zA-Z0-9_-]{11})$""")
            )
            for (p in patterns) {
                val m = p.find(url)
                if (m != null) return m.groupValues[1]
            }
            return null
        }
    }

    /**
     * Session state from the initial web page fetch.
     * Must be passed to Innertube API calls so YouTube does not reject them.
     */
    data class SessionData(
        val visitorData: String,
        val cookies: String
    )

    /**
     * Fetch the YouTube watch page to establish session cookies and extract visitor data.
     */
    suspend fun fetchSessionData(videoUrl: String): SessionData? {
        val pageClient = HttpClient {
            install(HttpRedirect) { checkHttpMethod = false }
            expectSuccess = false
        }
        return try {
            val response = pageClient.get(videoUrl) {
                header(HttpHeaders.UserAgent, BROWSER_UA)
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                header(HttpHeaders.Cookie, "CONSENT=YES+1; SOCS=CAI")
            }
            val html = response.bodyAsText()

            val visitorData = VISITOR_DATA_RE.find(html)?.groupValues?.get(1)
                ?: return null

            val cookieValues = response.headers.getAll(HttpHeaders.SetCookie)
                ?.mapNotNull { raw ->
                    raw.substringBefore(';').trim().takeIf { it.contains('=') }
                }
                ?: emptyList()

            val allCookies = buildList {
                add("CONSENT=YES+1")
                add("SOCS=CAI")
                addAll(cookieValues)
            }.joinToString("; ")

            println("[InnertubeClient] Session established: visitorData=${visitorData.take(30)}...")
            SessionData(visitorData, allCookies)
        } catch (e: Exception) {
            println("[InnertubeClient] Failed to fetch session data: ${e.message}")
            null
        } finally {
            pageClient.close()
        }
    }

    /**
     * Fetch the player response for a video using the ANDROID_VR client.
     *
     * @param videoId 11-char YouTube video ID
     * @param sessionData session from [fetchSessionData]; required for successful API calls.
     */
    suspend fun fetchPlayerResponse(
        videoId: String,
        sessionData: SessionData? = null
    ): JsonObject? {
        val apiClient = HttpClient {
            install(HttpRedirect) { checkHttpMethod = false }
            expectSuccess = false
        }
        return try {
            val body = buildJsonObject {
                put("videoId", videoId)
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.71.26")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("androidSdkVersion", 32)
                        put("userAgent", ANDROID_VR_UA)
                        put("osName", "Android")
                        put("osVersion", "12L")
                        put("hl", "en")
                        put("timeZone", "UTC")
                        put("utcOffsetMinutes", 0)
                    }
                }
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("html5Preference", "HTML5_PREF_WANTS")
                    }
                }
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val response = apiClient.post("$PLAYER_URL?prettyPrint=false") {
                setBody(TextContent(body.toString(), ContentType.Application.Json))
                header(HttpHeaders.UserAgent, ANDROID_VR_UA)
                header("X-YouTube-Client-Name", "28")
                header("X-YouTube-Client-Version", "1.71.26")
                header(HttpHeaders.Origin, "https://www.youtube.com")
                if (sessionData != null) {
                    header("X-Goog-Visitor-Id", sessionData.visitorData)
                    header(HttpHeaders.Cookie, sessionData.cookies)
                }
            }

            if (!response.status.isSuccess()) {
                println("[InnertubeClient] API returned HTTP ${response.status}")
                return null
            }

            val result = json.parseToJsonElement(response.bodyAsText()).jsonObject

            val status = result["playabilityStatus"]?.jsonObject
                ?.get("status")?.jsonPrimitive?.contentOrNull
            val reason = result["playabilityStatus"]?.jsonObject
                ?.get("reason")?.jsonPrimitive?.contentOrNull ?: ""
            println("[InnertubeClient] Player response: status=$status $reason")

            if (status != "OK") return null

            val sd = result["streamingData"]?.jsonObject ?: return null
            val formats = sd["formats"]?.jsonArray ?: JsonArray(emptyList())
            val adaptive = sd["adaptiveFormats"]?.jsonArray ?: JsonArray(emptyList())
            val allFormats = formats + adaptive
            if (allFormats.isEmpty()) {
                println("[InnertubeClient] No streaming formats in response")
                return null
            }

            val directCount = allFormats.count {
                it.jsonObject["url"]?.jsonPrimitive?.contentOrNull != null
            }
            println("[InnertubeClient] Got ${formats.size} formats + ${adaptive.size} adaptive ($directCount direct URLs)")
            result
        } catch (e: Exception) {
            println("[InnertubeClient] API call failed: ${e.message}")
            null
        } finally {
            apiClient.close()
        }
    }
}
