package com.example.tweakly.ui.gallery

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tweakly.data.model.*
import com.google.accompanist.permissions.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AnimatedContent(state.isSelectMode, label = "topbar") { selectMode ->
                if (selectMode) {
                    // Multi-select top bar
                    TopAppBar(
                        title = { Text("Выбрано: ${state.selectedIds.size}", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton({ vm.exitSelectMode() }) {
                                Icon(Icons.Default.Close, "Отмена")
                            }
                        },
                        actions = {
                            IconButton({ vm.selectAll() }) { Icon(Icons.Default.SelectAll, "Выбрать всё") }
                            IconButton({ vm.syncSelected() }) { Icon(Icons.Default.CloudUpload, "Синхронизировать") }
                            IconButton({ vm.deleteSelected(ctx) }) {
                                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Tweakly", fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        actions = {
                            if (!state.isPremium) {
                                TextButton(onSubscribeClick) {
                                    Icon(Icons.Default.Star, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Premium", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(onSearchClick) { Icon(Icons.Default.Search, "Поиск") }
                            Box {
                                IconButton({ showSortMenu = true }) { Icon(Icons.Default.Sort, "Сортировка") }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    listOf(
                                        SortOrder.DATE_DESC to "Сначала новые",
                                        SortOrder.DATE_ASC  to "Сначала старые",
                                        SortOrder.SIZE_DESC to "По размеру",
                                        SortOrder.NAME_ASC  to "По имени"
                                    ).forEach { (order, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { vm.setSortOrder(order); showSortMenu = false },
                                            leadingIcon = {
                                                if (state.sortOrder == order)
                                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        )
                                    }
                                }
                            }
                            if (state.isLoggedIn) {
                                IconButton({ vm.syncAll() }) { Icon(Icons.Default.Sync, "Синхронизировать") }
                            }
                            IconButton(onSettingsClick) { Icon(Icons.Default.Settings, "Настройки") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    ) { padding ->
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
            PullToRefreshBox(isRefreshing = state.isRefreshing,
                onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.flatItems.isEmpty() -> EmptyState(state.tab)
                    else -> MediaGrid(
                        items = state.flatItems,
                        selectedIds = state.selectedIds,
                        isSelectMode = state.isSelectMode,
                        onClick = { id ->
                            if (state.isSelectMode) vm.toggleSelect(id)
                            else onMediaClick(id)
                        },
                        onLongClick = { id -> vm.enterSelectMode(id) },
                        onFavorite = { item -> vm.toggleFavorite(item) }
                    )
                }
            }
        }
    }
}

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
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { item ->
            when (item) {
                is GalleryListItem.Header -> item(key = "h_${item.date}", span = { GridItemSpan(3) }) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Text(item.date, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is GalleryListItem.Cell -> item(key = "c_${item.media.id}") {
                    MediaCell(
                        media = item.media,
                        isSelected = item.media.id in selectedIds,
                        isSelectMode = isSelectMode,
                        onClick = { onClick(item.media.id) },
                        onLongClick = { onLongClick(item.media.id) },
                        onFavorite = { onFavorite(item.media) }
                    )
                }
                is GalleryListItem.PeopleGroupCell -> item(key = "pg_${item.group.groupId}") {
                    // People group cell
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
                        AsyncImage(model = item.group.previewUri, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color.Black.copy(.5f)).padding(4.dp)) {
                            Text("${item.group.count} фото", color = Color.White,
                                fontSize = 11.sp, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
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
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(3.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(media.uri).crossfade(true)
                .size(300, 300).allowHardware(true)
                .memoryCacheKey(media.uri).diskCacheKey(media.uri).build(),
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Selected overlay
        AnimatedVisibility(isSelected,
            enter = fadeIn(tween(100)), exit = fadeOut(tween(100))) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(.4f))) {
                Icon(Icons.Default.CheckCircle, null,
                    Modifier.align(Alignment.Center).size(32.dp),
                    tint = Color.White)
            }
        }
        // Select circle
        if (isSelectMode && !isSelected) {
            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp)
                .clip(CircleShape).border(2.dp, Color.White, CircleShape)
                .background(Color.Black.copy(.3f)))
        }
        // Video badge
        if (media.mediaType == MediaType.VIDEO) {
            Box(Modifier.align(Alignment.BottomStart).padding(4.dp)
                .background(Color.Black.copy(.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(10.dp), tint = Color.White)
                    Spacer(Modifier.width(2.dp))
                    Text(fmtDuration(media.duration), color = Color.White, fontSize = 10.sp)
                }
            }
        }
        // Favorite badge
        if (media.isFavorite) {
            Icon(Icons.Default.Favorite, null,
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp),
                tint = Color(0xFFFF4081))
        }
        // Sync badge
        when (media.syncStatus) {
            SyncStatusUi.SYNCED -> Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(Color(0xFF4CAF50).copy(.9f), RoundedCornerShape(4.dp)).padding(3.dp)) {
                Icon(Icons.Default.CloudDone, null, Modifier.size(10.dp), tint = Color.White)
            }
            SyncStatusUi.FAILED -> Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(Color.Red.copy(.8f), RoundedCornerShape(4.dp)).padding(3.dp)) {
                Icon(Icons.Default.Error, null, Modifier.size(10.dp), tint = Color.White)
            }
            else -> {}
        }
    }
}

@Composable
private fun GalleryTabs(current: GalleryTab, onTab: (GalleryTab) -> Unit) {
    val tabs = listOf(
        GalleryTab.ALL         to "Всё",
        GalleryTab.PHOTOS      to "Фото",
        GalleryTab.VIDEOS      to "Видео",
        GalleryTab.SCREENSHOTS to "Скрины",
        GalleryTab.FAVORITES   to "Избранное",
        GalleryTab.PEOPLE      to "Люди"
    )
    ScrollableTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == current }.coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.background, edgePadding = 12.dp) {
        tabs.forEach { (tab, label) ->
            Tab(selected = current == tab, onClick = { onTab(tab) },
                text = { Text(label, fontWeight = if (current == tab) FontWeight.SemiBold else FontWeight.Normal) })
        }
    }
}

@Composable
private fun StorageWarningBanner(usedPercent: Float, onUpgrade: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (usedPercent >= 1f) MaterialTheme.colorScheme.errorContainer
                             else MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (usedPercent >= 1f) "Хранилище заполнено" else "Хранилище ${(usedPercent*100).toInt()}%",
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
        Text("Tweakly нужен доступ к вашим фото и видео", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(12.dp)) { Text("Разрешить доступ") }
    }
}

@Composable
private fun EmptyState(tab: GalleryTab) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        val (icon, text) = when (tab) {
            GalleryTab.VIDEOS      -> Icons.Default.Videocam to "Видео не найдены"
            GalleryTab.SCREENSHOTS -> Icons.Default.CropFree to "Скриншоты не найдены"
            GalleryTab.FAVORITES   -> Icons.Default.FavoriteBorder to "Нет избранных фото"
            GalleryTab.PEOPLE      -> Icons.Default.People to "Лица не обнаружены"
            else -> Icons.Default.PhotoLibrary to "Медиафайлы не найдены"
        }
        Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f))
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fmtDuration(ms: Long): String {
    val s = TimeUnit.MILLISECONDS.toSeconds(ms)
    return "%d:%02d".format(s / 60, s % 60)
}
