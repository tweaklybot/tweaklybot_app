package com.example.tweakly.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.tweakly.data.model.SyncStatusUi
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photoId: Long,
    onBack: () -> Unit,
    viewModel: PhotoViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(photoId) { viewModel.loadPhoto(photoId) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Photo with zoom
        uiState.photo?.let { photo ->
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
            )
        }

        // Top bar
        TopAppBar(
            title = { Text(uiState.photo?.displayName ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
            },
            actions = {
                uiState.photo?.let { photo ->
                    if (photo.syncStatus == SyncStatusUi.SYNCED) {
                        Icon(Icons.Default.CloudDone, contentDescription = "Синхронизировано", tint = Color(0xFF4CAF50), modifier = Modifier.padding(8.dp))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Bottom action bar
        if (!uiState.isLoading) {
            BottomActionBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                onShare = { viewModel.sharePhoto(context) },
                onInfo = { viewModel.showInfoDialog() },
                onDelete = { viewModel.showDeleteDialog() },
                onOcr = { viewModel.performOcr(context) },
                isOcrLoading = uiState.isOcrLoading
            )
        }

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Удалить фото?") },
            text = { Text("Это действие нельзя отменить. Фото будет удалено с устройства.") },
            confirmButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog(); onBack() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) { Text("Отмена") }
            }
        )
    }

    // Info dialog
    if (uiState.showInfoDialog) {
        uiState.photo?.let { photo ->
            AlertDialog(
                onDismissRequest = { viewModel.hideInfoDialog() },
                title = { Text("Информация о фото") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Имя файла", photo.displayName)
                        InfoRow("Размер", formatFileSize(photo.size))
                        InfoRow("Разрешение", "${photo.width} × ${photo.height}")
                        InfoRow("Дата съёмки", formatDate(photo.dateTaken))
                        InfoRow("Синхронизация", when(photo.syncStatus) {
                            SyncStatusUi.SYNCED -> "✅ Синхронизировано"
                            SyncStatusUi.PENDING -> "🕐 Ожидает"
                            SyncStatusUi.FAILED -> "❌ Ошибка"
                            SyncStatusUi.NOT_SYNCED -> "☁️ Не синхронизировано"
                        })
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.hideInfoDialog() }) { Text("Закрыть") }
                }
            )
        }
    }

    // OCR result dialog
    uiState.ocrResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearOcrResult() },
            title = { Text("Распознанный текст") },
            text = {
                Column {
                    Text(result, modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 300.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(result))
                    viewModel.clearOcrResult()
                }) { Text("Копировать") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearOcrResult() }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun BottomActionBar(
    modifier: Modifier,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onOcr: () -> Unit,
    isOcrLoading: Boolean
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionButton(Icons.Default.Share, "Поделиться", onClick = onShare)
        ActionButton(Icons.Default.Info, "Информация", onClick = onInfo)
        ActionButton(Icons.Default.Delete, "Удалить", onClick = onDelete, tint = MaterialTheme.colorScheme.error)
        if (isOcrLoading) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp).padding(8.dp), color = Color.White)
        } else {
            ActionButton(Icons.Default.TextFields, "OCR", onClick = onOcr)
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> "%.1f МБ".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f КБ".format(bytes / 1_000.0)
        else -> "$bytes Б"
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "Неизвестно"
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}
