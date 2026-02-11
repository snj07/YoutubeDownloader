package com.ytdownloader.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ytdownloader.data.AppContainer
import com.ytdownloader.engine.EngineConfig
import com.ytdownloader.engine.EngineMode
import com.ytdownloader.domain.model.DownloadStatus
import com.ytdownloader.domain.model.OutputFormat
import com.ytdownloader.domain.model.QualityPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.awt.Dimension
import java.awt.Frame
import java.net.URL
import java.nio.file.Paths
import javax.imageio.ImageIO

fun main() = application {
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val appDir = Paths.get(System.getProperty("user.home"), ".ytdownloader").toString()
    val downloadsDir = Paths.get(System.getProperty("user.home"), "Downloads", "YouTubeDownloader").toString()
    val bundledFfmpegPath = remember { FfmpegLocator.resolveBundledFfmpeg() }
    val resolvedFfmpegPath = remember { FfmpegLocator.resolveFfmpeg() }
    val bundledYtDlpPath = remember { YtDlpLocator.resolveBundledYtDlp() }
    val defaultEngineMode = remember { EngineMode.KTOR }
    val defaultYtDlpPath = remember { bundledYtDlpPath.orEmpty() }
    val defaultFfmpegPath = remember { resolvedFfmpegPath.orEmpty() }
    var engineMode by remember { mutableStateOf(defaultEngineMode) }
    var ytDlpPath by remember { mutableStateOf(defaultYtDlpPath) }
    var ffmpegPath by remember { mutableStateOf(defaultFfmpegPath) }

    val engineConfig = remember(engineMode, ytDlpPath, ffmpegPath) {
        EngineConfig(
            mode = engineMode,
            ytDlpPath = normalizePath(ytDlpPath),
            ffmpegPath = normalizePath(ffmpegPath)
        )
    }
    val container = remember(engineConfig) { AppContainer(appDir, engineConfig) }
    val viewModel = remember(container) { DownloadsViewModel(container, appScope) }

    AppWindow(
        viewModel = viewModel,
        downloadsDir = downloadsDir,
        defaultEngineMode = defaultEngineMode,
        defaultYtDlpPath = defaultYtDlpPath,
        defaultFfmpegPath = defaultFfmpegPath,
        onExitRequest = ::exitApplication,
        engineMode = engineMode,
        onEngineModeChange = { engineMode = it },
        ytDlpPath = ytDlpPath,
        onYtDlpPathChange = { ytDlpPath = it },
        ffmpegPath = ffmpegPath,
        onFfmpegPathChange = { ffmpegPath = it },
        bundledYtDlpPath = bundledYtDlpPath,
        bundledFfmpegPath = bundledFfmpegPath
    )
}

@Composable
private fun ApplicationScope.AppWindow(
    viewModel: DownloadsViewModel,
    downloadsDir: String,
    defaultEngineMode: EngineMode,
    defaultYtDlpPath: String,
    defaultFfmpegPath: String,
    onExitRequest: () -> Unit,
    engineMode: EngineMode,
    onEngineModeChange: (EngineMode) -> Unit,
    ytDlpPath: String,
    onYtDlpPathChange: (String) -> Unit,
    ffmpegPath: String,
    onFfmpegPathChange: (String) -> Unit,
    bundledYtDlpPath: String?,
    bundledFfmpegPath: String?
) {
    val windowState = rememberWindowState(width = 1000.dp, height = 700.dp)
    Window(state = windowState, onCloseRequest = ::exitApplication, title = "YouTube Downloader") {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppContent(
                    viewModel = viewModel,
                    downloadsDir = downloadsDir,
                    defaultEngineMode = defaultEngineMode,
                    defaultYtDlpPath = defaultYtDlpPath,
                    defaultFfmpegPath = defaultFfmpegPath,
                    onExitRequest = onExitRequest,
                    engineMode = engineMode,
                    onEngineModeChange = onEngineModeChange,
                    ytDlpPath = ytDlpPath,
                    onYtDlpPathChange = onYtDlpPathChange,
                    ffmpegPath = ffmpegPath,
                    onFfmpegPathChange = onFfmpegPathChange,
                    bundledYtDlpPath = bundledYtDlpPath,
                    bundledFfmpegPath = bundledFfmpegPath
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    viewModel: DownloadsViewModel,
    downloadsDir: String,
    defaultEngineMode: EngineMode,
    defaultYtDlpPath: String,
    defaultFfmpegPath: String,
    onExitRequest: () -> Unit,
    engineMode: EngineMode,
    onEngineModeChange: (EngineMode) -> Unit,
    ytDlpPath: String,
    onYtDlpPathChange: (String) -> Unit,
    ffmpegPath: String,
    onFfmpegPathChange: (String) -> Unit,
    bundledYtDlpPath: String?,
    bundledFfmpegPath: String?
) {
    val url by viewModel.url.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val format by viewModel.format.collectAsState()
    val playlistItems by viewModel.playlistItems.collectAsState()
    val playlistLoading by viewModel.playlistLoading.collectAsState()
    val singleVideo by viewModel.singleVideo.collectAsState()
    val error by viewModel.error.collectAsState()
    val consentGranted by viewModel.consentGranted.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showDisclaimerInfo by remember { mutableStateOf(false) }
    var downloadPath by remember { mutableStateOf(downloadsDir) }

  

    if (showSettings) {
        SettingsDialog(
            downloadPath = downloadPath,
            onDownloadPathChange = { downloadPath = it },
            onResetDefault = {
                downloadPath = downloadsDir
                onEngineModeChange(defaultEngineMode)
                onYtDlpPathChange(defaultYtDlpPath)
                onFfmpegPathChange(defaultFfmpegPath)
            },
            engineMode = engineMode,
            onEngineModeChange = onEngineModeChange,
            ytDlpPath = ytDlpPath,
            onYtDlpPathChange = onYtDlpPathChange,
            ffmpegPath = ffmpegPath,
            onFfmpegPathChange = onFfmpegPathChange,
            bundledYtDlpPath = bundledYtDlpPath,
            bundledFfmpegPath = bundledFfmpegPath,
            onDismiss = { showSettings = false }
        )
    }

    if (!consentGranted) {
        ConsentDialog(
            onAccept = viewModel::grantConsent,
            onDecline = onExitRequest
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).widthIn(min = 900.dp).verticalScroll(rememberScrollState())
    ) {
        // Ensure the native window has a minimum size so right-side buttons remain visible.
        LaunchedEffect(Unit) {
            for (i in 0 until 10) {
                val frame = Frame.getFrames().firstOrNull { it.title == "YouTube Downloader" }
                if (frame != null) {
                    // Set a sensible minimum size (px) based on DPI-independent dp choice (~900x600)
                    frame.minimumSize = Dimension(900, 600)

                    // Try to load a packaged raster icon from resources (prefer PNG).
                    try {
                        val loader = Thread.currentThread().contextClassLoader
                        val stream = loader.getResourceAsStream("icons/download.png")
                            ?: loader.getResourceAsStream("icons/download@2x.png")
                        if (stream != null) {
                            val img = ImageIO.read(stream)
                            if (img != null) frame.iconImage = img
                        }
                    } catch (_: Throwable) {
                        // ignore failures; no runtime generation here
                    }

                    break
                }
                delay(100)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("YouTube Downloader", style = MaterialTheme.typography.h5)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { viewModel.reset() }) {
                Text("Reset")
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showDisclaimerInfo = true }) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Disclaimer Info")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showSettings = true }) {
                Text("Settings")
            }
        }
        Spacer(Modifier.height(16.dp))

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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.downloadSingle(downloadPath) },
                    enabled = consentGranted,
                    modifier = Modifier.widthIn(min = 140.dp)
                ) {
                    Text("Download Video")
                }
                Button(
                    onClick = { viewModel.downloadPlaylist(downloadPath) },
                    enabled = consentGranted,
                    modifier = Modifier.widthIn(min = 140.dp)
                ) {
                    Text("Download Selected")
                }
                Button(
                    onClick = { viewModel.downloadAllPlaylist(downloadPath) },
                    enabled = consentGranted,
                    modifier = Modifier.widthIn(min = 140.dp)
                ) {
                    Text("Download All")
                }
                OutlinedButton(
                    onClick = viewModel::previewPlaylist,
                    enabled = consentGranted,
                    modifier = Modifier.widthIn(min = 140.dp)
                ) {
                    Text("Preview Playlist")
                }
                OutlinedButton(
                    onClick = { viewModel.stopDownload() },
                    enabled = consentGranted,
                    modifier = Modifier.widthIn(min = 120.dp)
                ) {
                    Text("Stop")
                }
            }
            // select/deselect moved to appear after playlist preview
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error ?: "", color = MaterialTheme.colors.error)
        }

        if (playlistLoading) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }

        if (singleVideo != null) {
            Spacer(Modifier.height(16.dp))
            SingleVideoStatusSection(singleVideo!!, onStop = { viewModel.stopDownload() })
        }
        Spacer(Modifier.height(12.dp))

        if (playlistItems.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(
                    onClick = { viewModel.selectAllPlaylistItems(true) },
                    enabled = playlistItems.isNotEmpty(),
                    modifier = Modifier.widthIn(min = 140.dp)
                ) {
                    Text("Select All")
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = { viewModel.selectAllPlaylistItems(false) },
                    enabled = playlistItems.isNotEmpty(),
                    modifier = Modifier.widthIn(min = 140.dp)
                ) {
                    Text("Deselect All")
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        
        if (playlistItems.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Playlist preview (${playlistItems.size} videos)")
            Spacer(Modifier.height(8.dp))
            playlistItems.forEach { item ->
                PlaylistItemRow(item, viewModel)
                Spacer(Modifier.height(12.dp))
            }
        }

       

        if (showDisclaimerInfo) {
            DisclaimerDialog(onClose = { showDisclaimerInfo = false })
        }
    }
}

@Composable
private fun QualityDropdown(current: QualityPreference, onSelected: (QualityPreference) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Quality: ${current.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QualityPreference.values().forEach { option ->
                DropdownMenuItem(onClick = {
                    onSelected(option)
                    expanded = false
                }) {
                    Text(option.name)
                }
            }
        }
    }
}

@Composable
private fun FormatDropdown(current: OutputFormat, onSelected: (OutputFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Format: ${current.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OutputFormat.values().forEach { option ->
                DropdownMenuItem(onClick = {
                    onSelected(option)
                    expanded = false
                }) {
                    Text(option.name)
                }
            }
        }
    }
}

@Composable
private fun SingleVideoStatusSection(video: DownloadsViewModel.SingleVideoUi, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            ThumbnailImage(video.thumbnailUrl)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(video.title, style = MaterialTheme.typography.subtitle1, modifier = Modifier.weight(1f))
                    if (video.status == DownloadStatus.DOWNLOADING || video.status == DownloadStatus.QUEUED) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onStop) {
                            Text("Stop")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val progress = video.progress
                val totalBytes = progress?.totalBytes
                if (progress != null && totalBytes != null && totalBytes > 0) {
                    val ratio = (progress.downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(ratio, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    val downloadedMB = progress.downloadedBytes / (1024.0 * 1024.0)
                    val totalMB = totalBytes / (1024.0 * 1024.0)
                    Text("%.1f / %.1f MB".format(downloadedMB, totalMB), style = MaterialTheme.typography.caption)
                } else if (video.status == DownloadStatus.DOWNLOADING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                val statusColor = when (video.status) {
                    DownloadStatus.COMPLETED -> Color(0xFF2E7D32)
                    DownloadStatus.FAILED -> MaterialTheme.colors.error
                    DownloadStatus.CANCELLED -> Color.Gray
                    else -> Color.Unspecified
                }
                Text("Status: ${video.status}", color = statusColor)
                if (video.outputPath != null) {
                    Text("Saved to: ${video.outputPath}", style = MaterialTheme.typography.caption)
                }
                if (video.errorMessage != null) {
                    Text("Error: ${video.errorMessage}", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    item: DownloadsViewModel.PlaylistItemUi,
    viewModel: DownloadsViewModel
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Checkbox(checked = item.selected, onCheckedChange = { viewModel.setPlaylistItemSelected(item.id, it) })
        Spacer(Modifier.width(8.dp))
        ThumbnailImage(item.thumbnailUrl)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(item.title, style = MaterialTheme.typography.subtitle1)
            Text(item.id.value, style = MaterialTheme.typography.caption)
            if (item.status != null) {
                val progress = item.progress
                val totalBytes = progress?.totalBytes
                if (progress != null && totalBytes != null && totalBytes > 0) {
                    val ratio = (progress.downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(ratio, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(2.dp))
                    val downloadedMB = progress.downloadedBytes / (1024.0 * 1024.0)
                    val totalMB = totalBytes / (1024.0 * 1024.0)
                    Text("%.1f / %.1f MB".format(downloadedMB, totalMB), style = MaterialTheme.typography.caption)
                } else if (item.status == DownloadStatus.DOWNLOADING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                val statusColor = when (item.status) {
                    DownloadStatus.COMPLETED -> Color(0xFF2E7D32)
                    DownloadStatus.FAILED -> MaterialTheme.colors.error
                    DownloadStatus.CANCELLED -> Color.Gray
                    else -> Color.Unspecified
                }
                Text("Status: ${item.status}", color = statusColor)
                if (item.outputPath != null) {
                    Text("Saved to: ${item.outputPath}", style = MaterialTheme.typography.caption)
                }
            } else {
                Text("Status: Pending", style = MaterialTheme.typography.caption)
            }
        }
    }
}

@Composable
private fun ThumbnailImage(url: String?) {
    val imageState = produceState<ImageBitmap?>(initialValue = null, url) {
        value = if (url == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = URL(url).openStream().use { it.readBytes() }
                    SkiaImage.makeFromEncoded(bytes).asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val image = imageState.value
    if (image != null) {
        Image(bitmap = image, contentDescription = "Thumbnail", modifier = Modifier.size(120.dp, 68.dp))
    } else {
        Box(
            modifier = Modifier
                .size(120.dp, 68.dp)
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text("No image", style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun ConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
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
        dismissButton = {
            OutlinedButton(onClick = onDecline) {
                Text("I Disagree")
            }
        }
    )
}

@Composable
private fun SettingsDialog(
    downloadPath: String,
    onDownloadPathChange: (String) -> Unit,
    onResetDefault: () -> Unit,
    engineMode: EngineMode,
    onEngineModeChange: (EngineMode) -> Unit,
    ytDlpPath: String,
    onYtDlpPathChange: (String) -> Unit,
    ffmpegPath: String,
    onFfmpegPathChange: (String) -> Unit,
    bundledYtDlpPath: String?,
    bundledFfmpegPath: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Download path")
                OutlinedTextField(
                    value = downloadPath,
                    onValueChange = onDownloadPathChange,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Engine")
                EngineModeDropdown(engineMode, onEngineModeChange)
                if (engineMode != EngineMode.KTOR) {
                    Text("yt-dlp path")
                    OutlinedTextField(
                        value = ytDlpPath,
                        onValueChange = onYtDlpPathChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Optional if on PATH") }
                    )
                    if (bundledYtDlpPath != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onYtDlpPathChange(bundledYtDlpPath) }) {
                                Text("Use Bundled yt-dlp")
                            }
                            OutlinedButton(onClick = { onYtDlpPathChange("") }) {
                                Text("Clear")
                            }
                        }
                    }
                }
                Text("ffmpeg path")
                OutlinedTextField(
                    value = ffmpegPath,
                    onValueChange = onFfmpegPathChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional if on PATH") }
                )
                if (bundledFfmpegPath != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onFfmpegPathChange(bundledFfmpegPath) }) {
                            Text("Use Bundled ffmpeg")
                        }
                        OutlinedButton(onClick = { onFfmpegPathChange("") }) {
                            Text("Clear")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Quality and format are set in the main screen.", style = MaterialTheme.typography.caption)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResetDefault) {
                    Text("Use Default")
                }
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun EngineModeDropdown(current: EngineMode, onSelected: (EngineMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Mode: ${current.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EngineMode.values().forEach { option ->
                DropdownMenuItem(onClick = {
                    onSelected(option)
                    expanded = false
                }) {
                    Text(option.name)
                }
            }
        }
    }
}

private fun normalizePath(value: String): String? {
    val trimmed = value.trim()
    return if (trimmed.isEmpty()) null else trimmed
}

@Composable
private fun DisclaimerDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Disclaimer & Terms of Use") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Important legal notice:")
                Text("• This software simply automates downloading public YouTube content. It does NOT grant you any rights to copyrighted material.")
                Text("• You are solely responsible for ensuring that any content you download is permitted by applicable law and by the content owner's terms.")
                Text("• Do not use this tool to infringe copyrights, bypass access controls, or violate YouTube's Terms of Service. The authors and distributors of this software accept no liability for misuse.")
                Text("• If you intend to use this tool in an organizational or commercial context, consult legal counsel to confirm compliance with local laws and platform terms. This is not legal advice.")
                Text("• Features like conversion or extraction using ffmpeg and usage of yt-dlp are provided for convenience; third-party tool licenses and obligations still apply.")
                Text("• By using this software you confirm you have the necessary rights and permissions to download the requested content.")
                Spacer(Modifier.height(4.dp))
                Text("If you disagree with these terms, do not use this software.")
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Close")
            }
        },
        dismissButton = {}
    )
}
