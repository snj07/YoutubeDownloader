package com.ytdownloader.data.store

import com.ytdownloader.domain.model.DownloadId
import com.ytdownloader.engine.DownloadStateStore
import com.ytdownloader.engine.model.DownloadState
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

class FileDownloadStateStore(
    private val baseDirectory: String,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
) : DownloadStateStore {
    private val fileSystem = FileSystem.SYSTEM
    private val basePath = baseDirectory.toPath()

    override suspend fun get(id: DownloadId): DownloadState? {
        val path = statePath(id)
        if (!fileSystem.exists(path)) return null
        return runCatching {
            fileSystem.read(path) { json.decodeFromString(DownloadState.serializer(), readUtf8()) }
        }.getOrNull()
    }

    override suspend fun save(state: DownloadState) {
        val path = statePath(state.id)
        fileSystem.createDirectories(basePath)
        fileSystem.write(path) {
            writeUtf8(json.encodeToString(DownloadState.serializer(), state))
        }
    }

    override suspend fun delete(id: DownloadId) {
        fileSystem.delete(statePath(id), mustExist = false)
    }

    private fun statePath(id: DownloadId) = basePath.resolve("${id.value}.json")
}
