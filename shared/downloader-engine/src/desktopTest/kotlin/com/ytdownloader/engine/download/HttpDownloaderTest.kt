package com.ytdownloader.engine.download

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files

class HttpDownloaderTest {
    // @Test
    // fun downloadsBytesToFile() = kotlinx.coroutines.runBlocking {
    //     val content = ByteArray(512) { 1 }
    //     val engine = MockEngine { request ->
    //         respond(
    //             content = ByteReadChannel(content),
    //             headers = headersOf(HttpHeaders.ContentLength, content.size.toString())
    //         )
    //     }
    //     val client = HttpClient(engine)
    //     val controller = DownloadController()
    //     val tempDir = Files.createTempDirectory("ytdl-test").toFile()
    //     val outputPath = tempDir.resolve("file.bin").absolutePath

    //     val result = HttpDownloader(client, controller).download(
    //         url = "https://example.com/file",
    //         outputPath = outputPath,
    //         resumeBytes = 0L
    //     ) { _, _ -> }

    //     assertTrue(result is com.ytdownloader.domain.model.Outcome.Success)
    // }
}
