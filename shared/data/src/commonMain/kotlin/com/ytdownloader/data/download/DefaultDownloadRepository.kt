package com.ytdownloader.data.download

import com.ytdownloader.domain.model.DownloadError
import com.ytdownloader.domain.model.*
import com.ytdownloader.domain.repo.DownloadRepository
import com.ytdownloader.engine.DownloadSession
import com.ytdownloader.engine.DownloaderEngine
import com.ytdownloader.engine.DownloadStateStore
import com.ytdownloader.engine.model.DownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random
import kotlin.math.absoluteValue

class DefaultDownloadRepository(
    private val engine: DownloaderEngine,
    private val stateStore: DownloadStateStore,
    private val maxConcurrent: Int = 2
) : DownloadRepository {
    private val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val sessions = mutableMapOf<DownloadId, DownloadSession>()
    private val semaphore = Semaphore(maxConcurrent)

    private class TerminalEventException : Exception()

    override fun enqueue(request: DownloadRequest): Flow<DownloadEvent> = channelFlow {
        semaphore.withPermit {
            send(DownloadEvent.Queued(request.id))
            updateTask(DownloadTask(request.id, request.url, DownloadStatus.QUEUED, null, null, null))
            val resumeState = stateStore.get(request.id)
            val session = engine.startDownload(request, resumeState)
            sessions[request.id] = session

            try {
                session.events.collect { event ->
                    handleEvent(event, request)
                    send(event)
                    if (event is DownloadEvent.Completed || event is DownloadEvent.Failed || event is DownloadEvent.Cancelled) {
                        throw TerminalEventException()
                    }
                }
            } catch (_: TerminalEventException) {
                // Flow completed after terminal event
            }
        }
    }

    override suspend fun pause(id: DownloadId) {
        sessions[id]?.pause()
    }

    override suspend fun resume(id: DownloadId) {
        sessions[id]?.resume()
    }

    override suspend fun cancel(id: DownloadId) {
        sessions[id]?.cancel()
    }

    override fun observeTasks(): Flow<List<DownloadTask>> = tasks.asStateFlow()

    private suspend fun handleEvent(event: DownloadEvent, request: DownloadRequest) {
        when (event) {
            is DownloadEvent.Queued -> updateStatus(event.id, DownloadStatus.QUEUED)
            is DownloadEvent.Started -> updateStatus(event.id, DownloadStatus.DOWNLOADING)
            is DownloadEvent.Progress -> {
                updateProgress(event.id, event.progress)
                persistState(request, event.progress)
            }
            is DownloadEvent.Paused -> updateStatus(event.id, DownloadStatus.PAUSED)
            is DownloadEvent.Resumed -> updateStatus(event.id, DownloadStatus.DOWNLOADING)
            is DownloadEvent.Completed -> {
                updateCompleted(event.id, event.filePath)
                stateStore.delete(event.id)
            }
            is DownloadEvent.Failed -> {
                if (event.error is DownloadError.Cancelled) {
                    updateStatus(event.id, DownloadStatus.CANCELLED)
                } else {
                    updateStatus(event.id, DownloadStatus.FAILED)
                }
                stateStore.delete(event.id)
            }
            is DownloadEvent.Cancelled -> {
                updateStatus(event.id, DownloadStatus.CANCELLED)
                stateStore.delete(event.id)
            }
        }
    }

    private fun updateStatus(id: DownloadId, status: DownloadStatus) {
        tasks.update { list ->
            val current = list.find { it.id == id }
            if (current == null) list else list.map { if (it.id == id) it.copy(status = status) else it }
        }
    }

    private fun updateProgress(id: DownloadId, progress: DownloadProgress) {
        tasks.update { list ->
            list.map {
                if (it.id == id) it.copy(status = DownloadStatus.DOWNLOADING, progress = progress) else it
            }
        }
    }

    private fun updateCompleted(id: DownloadId, path: String) {
        tasks.update { list ->
            list.map {
                if (it.id == id) it.copy(status = DownloadStatus.COMPLETED, outputPath = path) else it
            }
        }
    }

    private fun updateTask(task: DownloadTask) {
        tasks.update { list ->
            if (list.any { it.id == task.id }) list else list + task
        }
    }

    private suspend fun persistState(request: DownloadRequest, progress: DownloadProgress) {
        val state = DownloadState(
            id = request.id,
            url = request.url,
            outputDirectory = request.outputDirectory,
            fileName = request.id.value,
            outputFormat = request.outputFormat,
            qualityPreference = request.qualityPreference,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            eTag = null,
            lastModified = null,
            updatedAtEpochMillis = Random.nextLong().absoluteValue
        )
        stateStore.save(state)
    }
}
