package com.example.tweakly.ui.gallery

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.model.MediaType
import com.example.tweakly.data.model.SyncStatusUi
import com.google.accompanist.permissions.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onMediaClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    vm: GalleryViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoLibrary, null,
                            Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Tweakly", fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = { /* sync */ }) {
                            Icon(Icons.Default.Sync, "Синхронизировать")
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (!perms.allPermissionsGranted) {
            PermissionScreen(Modifier.padding(padding)) { perms.launchMultiplePermissionRequest() }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
            // Tabs
            GalleryTabs(state.tab, onTab = { vm.setTab(it) })

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    state.groupedMedia.isEmpty() -> EmptyState(state.tab)
                    else -> MediaGrid(state.groupedMedia, onMediaClick)
                }
            }
        }
    }

    // Error snackbar
    state.error?.let { err ->
        LaunchedEffect(err) { vm.clearError() }
    }
}

@Composable
private fun GalleryTabs(current: GalleryTab, onTab: (GalleryTab) -> Unit) {
    val tabs = listOf(
        GalleryTab.ALL to "Всё",
        GalleryTab.PHOTOS to "Фото",
        GalleryTab.VIDEOS to "Видео",
        GalleryTab.SCREENSHOTS to "Скрины"
    )
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == current },
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = 12.dp
    ) {
        tabs.forEach { (tab, label) ->
            Tab(
                selected = current == tab,
                onClick = { onTab(tab) },
                text = { Text(label, fontWeight = if (current == tab) FontWeight.SemiBold else FontWeight.Normal) }
            )
        }
    }
}

@Composable
private fun MediaGrid(grouped: Map<String, List<MediaItem>>, onClick: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)) {
        grouped.forEach { (date, items) ->
            stickyHeader(key = "header_$date") {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text(date, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "grid_$date") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    Modifier.fillMaxWidth().heightIn(max = 5000.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    userScrollEnabled = false
                ) {
                    items(items, key = { it.id }) { item ->
                        MediaCell(item) { onClick(item.id) }
                    }
                }
            }
            item(key = "spacer_$date") { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun MediaCell(item: MediaItem, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video duration badge
        if (item.mediaType == MediaType.VIDEO) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .background(Color.Black.copy(.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(10.dp), tint = Color.White)
                    Spacer(Modifier.width(2.dp))
                    Text(fmtDuration(item.duration), color = Color.White, fontSize = 10.sp)
                }
            }
        }

        // Sync badge
        if (item.syncStatus == SyncStatusUi.SYNCED) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(.85f), RoundedCornerShape(4.dp))
                    .padding(3.dp)
            ) {
                Icon(Icons.Default.CloudDone, null, Modifier.size(10.dp), tint = Color.White)
            }
        }
    }
}

private fun fmtDuration(ms: Long): String {
    val s = TimeUnit.MILLISECONDS.toSeconds(ms)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PermissionScreen(modifier: Modifier, onRequest: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp),
        Alignment.CenterHorizontally, Arrangement.Center) {
        Icon(Icons.Default.Lock, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Нужен доступ к медиафайлам", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Tweakly нужен доступ к фото и видео на вашем устройстве",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(12.dp)) {
            Text("Разрешить доступ")
        }
    }
}

@Composable
private fun EmptyState(tab: GalleryTab) {
    Column(Modifier.fillMaxSize().padding(32.dp),
        Alignment.CenterHorizontally, Arrangement.Center) {
        val (icon, text) = when (tab) {
            GalleryTab.VIDEOS      -> Icons.Default.Videocam to "Видео не найдены"
            GalleryTab.SCREENSHOTS -> Icons.Default.CropFree to "Скриншоты не найдены"
            else                   -> Icons.Default.PhotoLibrary to "Медиафайлы не найдены"
        }
        Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f))
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
