package com.example.tweakly.ui.viewer

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.model.MediaType
import com.example.tweakly.data.model.SyncStatusUi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    mediaId: Long,
    onBack: () -> Unit,
    onEdit: (uri: String, name: String) -> Unit = { _, _ -> },
    vm: ViewerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(mediaId) { vm.loadAll(mediaId) }  // loads current + neighbours

    LaunchedEffect(state.deleteSuccess) {
        if (state.deleteSuccess) { snackbar.showSnackbar("Файл удалён"); onBack() }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { if (it.resultCode == android.app.Activity.RESULT_OK) vm.onDeleteConfirmed() }

    var barsVisible by remember { mutableStateOf(true) }
    var lastTap by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastTap) {
        delay(3000)
        if (System.currentTimeMillis() - lastTap >= 2900) barsVisible = false
    }
    fun onTap() { barsVisible = true; lastTap = System.currentTimeMillis() }

    // Horizontal pager for swipe between photos
    val allItems = state.allItems
    val startPage = allItems.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage) { allItems.size }

    // Update current item when page changes
    LaunchedEffect(pagerState.currentPage) {
        if (allItems.isNotEmpty()) {
            val id = allItems[pagerState.currentPage].id
            if (id != state.item?.id) vm.loadCurrent(id)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Black) { _ ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {

            if (allItems.isEmpty() && state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else if (allItems.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { allItems[it].id }
                ) { page ->
                    val item = allItems[page]
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTap() } }) {
                        when (item.mediaType) {
                            MediaType.VIDEO -> VideoPlayer(item.uri)
                            else -> ZoomablePhoto(item.uri) { onTap() }
                        }
                    }
                }
            }

            // Top bar
            AnimatedVisibility(barsVisible, modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
                exit  = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it }) {
                Box(Modifier.fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(.75f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp)) {
                    IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White) }
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.item?.displayName ?: "", color = Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        if (allItems.size > 1) {
                            Text("${pagerState.currentPage + 1} / ${allItems.size}",
                                color = Color.White.copy(.7f), fontSize = 11.sp)
                        }
                    }
                    if (state.item?.syncStatus == SyncStatusUi.SYNCED)
                        Icon(Icons.Default.CloudDone, null,
                            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                            tint = Color(0xFF4CAF50))
                }
            }

            // Bottom action bar
            AnimatedVisibility(barsVisible && !state.isLoading,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                exit  = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it }) {
                Box(Modifier.fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(.8f))))
                    .navigationBarsPadding().padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        ViewerBtn(Icons.Default.Share, "Поделиться") { state.item?.let { vm.share(ctx) } }
                        ViewerBtn(Icons.Default.Edit, "Редактор") {
                            state.item?.let { onEdit(it.uri, it.displayName) }
                        }
                        ViewerBtn(Icons.Default.Info, "Инфо") { vm.showInfoDialog() }
                        ViewerBtn(Icons.Default.Delete, "Удалить", tint = Color(0xFFFF5252)) { vm.showDeleteDialog() }
                        if (state.item?.mediaType == MediaType.PHOTO) {
                            if (state.isOcrRunning) BtnLoader() else ViewerBtn(Icons.Default.TextFields, "OCR") { vm.runOcr(ctx) }
                            if (state.isQrRunning)  BtnLoader() else ViewerBtn(Icons.Default.QrCodeScanner, "QR") { vm.scanQr(ctx) }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (state.showDeleteDialog) {
        AlertDialog(onDismissRequest = { vm.hideDeleteDialog() },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить файл?") },
            text = { Text("Файл будет удалён с устройства.") },
            confirmButton = {
                TextButton({
                    vm.hideDeleteDialog()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vm.deleteMedia(ctx, deleteLauncher)
                    else vm.deleteMedia(ctx)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ vm.hideDeleteDialog() }) { Text("Отмена") } })
    }

    if (state.showInfoDialog) {
        state.item?.let { item ->
            AlertDialog(onDismissRequest = { vm.hideInfoDialog() },
                title = { Text("Информация о файле") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                        InfoRow("Имя файла", item.displayName)
                        InfoRow("Размер", fmtSize(item.size))
                        InfoRow("Разрешение", "${item.width} × ${item.height}")
                        if (item.mediaType == MediaType.VIDEO) {
                            val s = item.duration / 1000
                            InfoRow("Длительность", "%d:%02d".format(s / 60, s % 60))
                        }
                        InfoRow("Дата съёмки", fmtDate(item.dateTaken))
                        InfoRow("Тип", when (item.mediaType) {
                            MediaType.VIDEO -> "Видео"; MediaType.SCREENSHOT -> "Скриншот"; else -> "Фото"
                        })
                        InfoRow("Синхронизация", when (item.syncStatus) {
                            SyncStatusUi.SYNCED -> "✅ Синхронизировано"
                            SyncStatusUi.PENDING -> "🕐 Ожидает загрузки"
                            SyncStatusUi.FAILED -> "❌ Ошибка загрузки"
                            else -> "☁️ Не синхронизировано"
                        })
                        item.remotePath?.let { InfoRow("Путь в облаке", it) }
                    }
                },
                confirmButton = { TextButton({ vm.hideInfoDialog() }) { Text("Закрыть") } })
        }
    }

    state.ocrResult?.let { text ->
        AlertDialog(onDismissRequest = { vm.clearOcr() },
            icon = { Icon(Icons.Default.TextFields, null) },
            title = { Text("Распознанный текст") },
            text = {
                if (text == "Текст не обнаружен") {
                    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer {
                        Text(text, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                    }
                }
            },
            confirmButton = {
                if (text != "Текст не обнаружен") {
                    TextButton({ clipboard.setText(AnnotatedString(text)); vm.clearOcr() }) { Text("Скопировать") }
                } else {
                    TextButton({ vm.clearOcr() }) { Text("Закрыть") }
                }
            },
            dismissButton = if (text != "Текст не обнаружен") {
                { TextButton({ vm.clearOcr() }) { Text("Закрыть") } }
            } else null
        )
    }

    state.qrResult?.let { text ->
        val isUrl = text.startsWith("http://") || text.startsWith("https://")
        AlertDialog(onDismissRequest = { vm.clearQr() },
            icon = { Icon(Icons.Default.QrCode, null) },
            title = { Text("QR / Штрих-код") },
            text = { SelectionContainer { Text(text) } },
            confirmButton = {
                if (isUrl) TextButton({ uriHandler.openUri(text); vm.clearQr() }) { Text("Открыть ссылку") }
                else TextButton({ clipboard.setText(AnnotatedString(text)); vm.clearQr() }) { Text("Скопировать") }
            },
            dismissButton = { TextButton({ vm.clearQr() }) { Text("Закрыть") } })
    }

    state.error?.let { err ->
        LaunchedEffect(err) { snackbar.showSnackbar(err); vm.clearError() }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePhoto(uri: String, onTap: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Build a proper ImageRequest so Coil can resolve content:// URIs from MediaStore
    val request = remember(uri) {
        ImageRequest.Builder(ctx)
            .data(uri)
            .crossfade(true)
            // Full resolution for viewer — no size restriction
            .memoryCacheKey(uri)
            .diskCacheKey(uri)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale,
                translationX = offsetX, translationY = offsetY)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(.5f, 6f)
                    offsetX += pan.x * scale; offsetY += pan.y * scale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.5f) { scale = 1f; offsetX = 0f; offsetY = 0f }
                        else scale = 2.5f
                    }
                )
            }
    )
}

@Composable
private fun VideoPlayer(uri: String) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(ExoMediaItem.fromUri(Uri.parse(uri))); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(factory = { c ->
        PlayerView(c).apply { this.player = player; useController = true }
    }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ViewerBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    tint: Color = Color.White, onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick) { Icon(icon, label, tint = tint) }
        Text(label, color = Color.White.copy(.8f), fontSize = 10.sp)
    }
}

@Composable
private fun BtnLoader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(.6f))
    }
}

private fun fmtSize(b: Long) = when { b >= 1_000_000 -> "%.1f МБ".format(b / 1_000_000.0); b >= 1_000 -> "%.1f КБ".format(b / 1_000.0); else -> "$b Б" }
private fun fmtDate(ts: Long) = if (ts == 0L) "Неизвестно" else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
