package com.ytdownloader.domain.model

sealed class DownloadError(
    override val message: String,
    override val cause: Throwable? = null
) : DomainError {
    class InvalidUrl(val url: String) : DownloadError("Invalid URL: $url")
    class NetworkFailure(override val cause: Throwable?) : DownloadError(
        "Network failure: ${cause?.message ?: "unknown"}",
        cause
    )
    class Throttled : DownloadError("YouTube throttling detected")
    class FormatNotAvailable : DownloadError("Requested format is not available")
    class PlaylistPrivate : DownloadError("Playlist is private or unavailable")
    class VideoUnavailable : DownloadError("Video unavailable")
    class ConversionFailed(override val cause: Throwable?) : DownloadError("Audio conversion failed", cause)
    class StorageFailure(override val cause: Throwable?) : DownloadError("Storage failure", cause)
    class Cancelled : DownloadError("Download cancelled")
    class Unknown(override val cause: Throwable?) : DownloadError("Unknown error", cause)
}
