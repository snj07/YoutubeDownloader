package com.ytdownloader.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ytdownloader.data.AppContainer
import com.ytdownloader.domain.model.DownloadStatus
import com.ytdownloader.domain.model.OutputFormat
import com.ytdownloader.domain.model.QualityPreference
import com.ytdownloader.engine.EngineConfig
import com.ytdownloader.engine.EngineMode
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appDir = remember { context.filesDir.absolutePath }
    val downloadsDir = remember {
        File(context.getExternalFilesDir(null), "YouTubeDownloader").absolutePath
    }
    val ffmpegPath = remember { FfmpegInstaller.ensureBundledFfmpeg(context) }
    val engineConfig = remember {
        EngineConfig(
            mode = EngineMode.KTOR,
            ffmpegPath = ffmpegPath
        )
    }
    val container = remember { AppContainer(appDir, engineConfig) }
    val viewModel = remember { AndroidDownloadsViewModel(container, scope) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AndroidAppContent(viewModel, downloadsDir)
        }
    }
}

@Composable
private fun AndroidAppContent(viewModel: AndroidDownloadsViewModel, downloadsDir: String) {
    val url by viewModel.url.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val format by viewModel.format.collectAsState()
    val playlistPreview by viewModel.playlistPreview.collectAsState()
    val tasks by viewModel.tasks.collectAsState(initial = emptyList())
    val error by viewModel.error.collectAsState()
    val consentGranted by viewModel.consentGranted.collectAsState()

    if (!consentGranted) {
        ConsentDialog(onAccept = viewModel::grantConsent)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("YouTube Downloader", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = url,
            onValueChange = viewModel::updateUrl,
            label = { Text("Video or playlist URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QualityDropdown(quality, viewModel::updateQuality)
            FormatDropdown(format, viewModel::updateFormat)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.downloadSingle(downloadsDir) }, enabled = consentGranted) {
                Text("Download Video")
            }
            OutlinedButton(onClick = { viewModel.downloadPlaylist(downloadsDir) }, enabled = consentGranted) {
                Text("Download Playlist")
            }
            OutlinedButton(onClick = viewModel::previewPlaylist) {
                Text("Preview Playlist")
            }
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", color = MaterialTheme.colorScheme.error)
        }

        if (playlistPreview.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Playlist preview (${playlistPreview.size} videos)")
            playlistPreview.take(10).forEach { id ->
                Text("- ${id.value}")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Download Queue", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tasks) { task ->
                DownloadTaskRow(task, viewModel)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun QualityDropdown(current: QualityPreference, onSelected: (QualityPreference) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Quality: ${current.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QualityPreference.values().forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, onClick = {
                    onSelected(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun FormatDropdown(current: OutputFormat, onSelected: (OutputFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Format: ${current.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OutputFormat.values().forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, onClick = {
                    onSelected(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(task: com.ytdownloader.domain.model.DownloadTask, viewModel: AndroidDownloadsViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(task.title ?: task.url, style = MaterialTheme.typography.titleSmall)
        val progress = task.progress
        val totalBytes = progress?.totalBytes
        if (progress != null && totalBytes != null) {
            val ratio = (progress.downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
        } else if (task.status == DownloadStatus.DOWNLOADING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${task.status}")
            OutlinedButton(onClick = { viewModel.pauseDownload(task.id) }, enabled = task.status == DownloadStatus.DOWNLOADING) {
                Text("Pause")
            }
            OutlinedButton(onClick = { viewModel.resumeDownload(task.id) }, enabled = task.status == DownloadStatus.PAUSED) {
                Text("Resume")
            }
            OutlinedButton(onClick = { viewModel.cancelDownload(task.id) }, enabled = task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Text("Cancel")
            }
        }

        if (task.outputPath != null) {
            Text("Saved to: ${task.outputPath}")
        }
    }
}

@Composable
private fun ConsentDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Terms & Disclaimer") },
        text = {
            Column {
                Text("This tool is for educational and personal backup use only.")
                Text("You must respect YouTube Terms of Service and content owner rights.")
                Text("By continuing, you confirm you have the right to download this content.")
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("I Agree")
            }
        },
        dismissButton = {}
    )
}
