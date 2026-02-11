package com.ytdownloader.engine.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal enum class ControlState {
    RUNNING,
    PAUSED,
    CANCELLED
}

internal class DownloadController {
    private val state = MutableStateFlow(ControlState.RUNNING)

    suspend fun pause() {
        if (state.value != ControlState.CANCELLED) {
            state.value = ControlState.PAUSED
        }
    }

    suspend fun resume() {
        if (state.value != ControlState.CANCELLED) {
            state.value = ControlState.RUNNING
        }
    }

    suspend fun cancel() {
        state.value = ControlState.CANCELLED
    }

    suspend fun awaitIfPaused() {
        if (state.value == ControlState.PAUSED) {
            state.first { it != ControlState.PAUSED }
        }
    }

    fun isCancelled(): Boolean = state.value == ControlState.CANCELLED
}
