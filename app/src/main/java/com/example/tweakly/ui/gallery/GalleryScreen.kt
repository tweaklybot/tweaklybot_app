package com.example.tweakly.ui.gallery

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.tweakly.data.model.*
import com.google.accompanist.permissions.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onMediaClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onSubscribeClick: () -> Unit = {},
    vm: GalleryViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }

    val permissions = if (Build.VERSION.SDK_INT >= 33)
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val perms = rememberMultiplePermissionsState(permissions) { grants ->
        if (grants.values.all { it }) vm.loadMedia()
    }
    LaunchedEffect(Unit) {
        if (!perms.allPermissionsGranted) perms.launchMultiplePermissionRequest()
        else vm.loadMedia()
    }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbar.showSnackbar(it); vm.clearSnackbar() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AnimatedContent(state.isSelectMode, label = "topbar") { sel ->
                if (sel) {
                    TopAppBar(
                        title = { Text("Выбрано: ${state.selectedIds.size}", fontWeight = FontWeight.Bold) },
                        navigationIcon = { IconButton({ vm.exitSelectMode() }) { Icon(Icons.Default.Close, null) } },
                        actions = {
                            IconButton({ vm.selectAll() }) { Icon(Icons.Default.SelectAll, null) }
                            IconButton({ vm.syncSelected() }) { Icon(Icons.Default.CloudUpload, null) }
                            IconButton({ vm.deleteSelected(ctx) }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Tweakly", fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        actions = {
                            if (!state.isPremium) {
                                TextButton(onSubscribeClick) {
                                    Icon(Icons.Default.Star, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Pro", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(onSearchClick) { Icon(Icons.Default.Search, null) }
                            Box {
                                IconButton({ showSortMenu = true }) { Icon(Icons.Default.Sort, null) }
                                DropdownMenu(showSortMenu, { showSortMenu = false }) {
                                    listOf(SortOrder.DATE_DESC to "Сначала новые",
                                        SortOrder.DATE_ASC to "Сначала старые",
                                        SortOrder.SIZE_DESC to "По размеру",
                                        SortOrder.NAME_ASC to "По имени")
                                        .forEach { (order, label) ->
                                            DropdownMenuItem(text = { Text(label) },
                                                onClick = { vm.setSortOrder(order); showSortMenu = false },
                                                leadingIcon = {
                                                    if (state.sortOrder == order)
                                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                })
                                        }
                                }
                            }
                            if (state.isLoggedIn) {
                                IconButton({ vm.syncAll() }) { Icon(Icons.Default.Sync, null) }
                            }
                            IconButton(onSettingsClick) { Icon(Icons.Default.Settings, null) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            }
        }) { padding ->

        if (!perms.allPermissionsGranted) {
            PermissionScreen(Modifier.padding(padding)) { perms.launchMultiplePermissionRequest() }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
            AnimatedVisibility(state.showStorageWarning,
                enter = expandVertically(tween(200)), exit = shrinkVertically(tween(200))) {
                StorageWarningBanner(state.storagePercent, onSubscribeClick)
            }
            GalleryTabs(state.tab) { vm.setTab(it) }
            PullToRefreshBox(state.isRefreshing, { vm.refresh() }, Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> ShimmerGrid()
                    state.flatItems.isEmpty() -> EmptyState(state.tab)
                    else -> MediaGrid(
                        items = state.flatItems,
                        selectedIds = state.selectedIds,
                        isSelectMode = state.isSelectMode,
                        onClick = { id -> if (state.isSelectMode) vm.toggleSelect(id) else onMediaClick(id) },
                        onLongClick = { vm.enterSelectMode(it) },
                        onFavorite = { vm.toggleFavorite(it) }
                    )
                }
            }
        }
    }
}

// ── Optimized grid — single LazyVerticalGrid, no nesting ─────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    items: List<GalleryListItem>,
    selectedIds: Set<Long>,
    isSelectMode: Boolean,
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit,
    onFavorite: (MediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { listItem ->
            when (listItem) {
                is GalleryListItem.Header -> item(
                    key = "h_${listItem.date}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Text(listItem.date,
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is GalleryListItem.Cell -> item(key = "c_${listItem.media.id}") {
                    MediaCell(
                        media = listItem.media,
                        isSelected = listItem.media.id in selectedIds,
                        isSelectMode = isSelectMode,
                        onClick = { onClick(listItem.media.id) },
                        onLongClick = { onLongClick(listItem.media.id) },
                        onFavorite = { onFavorite(listItem.media) }
                    )
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    media: MediaItem,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: () -> Unit
) {
    val ctx = LocalContext.current
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(3.dp))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)) {

        // Optimised: explicit thumbnail size + disk cache key
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(media.uri)
                .size(256, 256)
                .crossfade(200)
                .allowHardware(true)
                .memoryCacheKey(media.uri)
                .diskCacheKey(media.uri)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { ShimmerBox() },
            error = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    Alignment.Center) {
                    Icon(Icons.Default.BrokenImage, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f))
                }
            }
        )

        // Selected overlay
        AnimatedVisibility(isSelected, enter = fadeIn(tween(100)), exit = fadeOut(tween(100))) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(.45f))) {
                Icon(Icons.Default.CheckCircle, null,
                    Modifier.align(Alignment.Center).size(32.dp), tint = Color.White)
            }
        }
        if (isSelectMode && !isSelected) {
            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp)
                .clip(CircleShape).border(2.dp, Color.White, CircleShape)
                .background(Color.Black.copy(.3f)))
        }

        // Video duration badge
        if (media.mediaType == MediaType.VIDEO) {
            Box(Modifier.align(Alignment.BottomStart).padding(4.dp)
                .background(Color.Black.copy(.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(10.dp), tint = Color.White)
                    Spacer(Modifier.width(2.dp))
                    Text(fmtDur(media.duration), color = Color.White, fontSize = 10.sp)
                }
            }
        }

        // Favorite
        if (media.isFavorite)
            Icon(Icons.Default.Favorite, null,
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp),
                tint = Color(0xFFFF4081))

        // Sync badge
        when (media.syncStatus) {
            SyncStatusUi.SYNCED -> Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(Color(0xFF4CAF50).copy(.9f), RoundedCornerShape(4.dp)).padding(3.dp)) {
                Icon(Icons.Default.CloudDone, null, Modifier.size(10.dp), tint = Color.White)
            }
            SyncStatusUi.FAILED -> Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(Color.Red.copy(.85f), RoundedCornerShape(4.dp)).padding(3.dp)) {
                Icon(Icons.Default.Error, null, Modifier.size(10.dp), tint = Color.White)
            }
            else -> {}
        }
    }
}

// ── Shimmer skeleton while loading ───────────────────────────────────────────
@Composable
private fun ShimmerGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(30) { Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(3.dp))) { ShimmerBox() } }
    }
}

@Composable
private fun ShimmerBox() {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant.copy(.4f),
        MaterialTheme.colorScheme.surfaceVariant
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer_x"
    )
    Box(Modifier.fillMaxSize().background(
        brush = Brush.linearGradient(shimmerColors,
            start = Offset(translateX, 0f), end = Offset(translateX + 300f, 300f))
    ))
}

@Composable
private fun GalleryTabs(current: GalleryTab, onTab: (GalleryTab) -> Unit) {
    val tabs = listOf(GalleryTab.ALL to "Всё", GalleryTab.PHOTOS to "Фото",
        GalleryTab.VIDEOS to "Видео", GalleryTab.SCREENSHOTS to "Скрины",
        GalleryTab.FAVORITES to "Избранное", GalleryTab.PEOPLE to "Люди")
    ScrollableTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == current }.coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.background, edgePadding = 12.dp) {
        tabs.forEach { (tab, label) ->
            Tab(selected = current == tab, onClick = { onTab(tab) },
                text = { Text(label, fontWeight = if (current == tab) FontWeight.SemiBold else FontWeight.Normal) })
        }
    }
}

@Composable
private fun StorageWarningBanner(pct: Float, onUpgrade: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pct >= 1f) MaterialTheme.colorScheme.errorContainer
                             else MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (pct >= 1f) "Хранилище заполнено" else "Хранилище ${(pct * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onUpgrade, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Premium", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PermissionScreen(modifier: Modifier, onRequest: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Доступ к медиафайлам", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Нужен доступ к вашим фото и видео", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(12.dp)) { Text("Разрешить доступ") }
    }
}

@Composable
private fun EmptyState(tab: GalleryTab) {
    Column(Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        val (icon, text) = when (tab) {
            GalleryTab.VIDEOS -> Icons.Default.Videocam to "Видео не найдены"
            GalleryTab.SCREENSHOTS -> Icons.Default.CropFree to "Скриншоты не найдены"
            GalleryTab.FAVORITES -> Icons.Default.FavoriteBorder to "Нет избранных"
            GalleryTab.PEOPLE -> Icons.Default.People to "Лица не обнаружены"
            else -> Icons.Default.PhotoLibrary to "Медиафайлы не найдены"
        }
        Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f))
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fmtDur(ms: Long) = "%d:%02d".format(
    TimeUnit.MILLISECONDS.toSeconds(ms) / 60, TimeUnit.MILLISECONDS.toSeconds(ms) % 60)
