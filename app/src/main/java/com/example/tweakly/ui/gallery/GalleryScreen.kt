package com.example.tweakly.ui.gallery

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.tweakly.data.model.PhotoUiModel
import com.example.tweakly.data.model.SyncStatusUi
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onPhotoClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions) { grants ->
        if (grants.values.all { it }) viewModel.loadPhotos()
    }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        } else {
            viewModel.loadPhotos()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tweakly", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!permissionsState.allPermissionsGranted) {
            PermissionDeniedContent(
                modifier = Modifier.padding(paddingValues),
                onGrantPermission = { permissionsState.launchMultiplePermissionRequest() }
            )
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.photosByDate.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.photosByDate.isEmpty() -> {
                    EmptyGalleryContent()
                }
                else -> {
                    PhotoGrid(
                        photosByDate = uiState.photosByDate,
                        onPhotoClick = onPhotoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photosByDate: Map<String, List<PhotoUiModel>>,
    onPhotoClick: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        photosByDate.forEach { (date, photos) ->
            item {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 2000.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    userScrollEnabled = false
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoGridItem(photo = photo, onClick = { onPhotoClick(photo.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridItem(photo: PhotoUiModel, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (photo.syncStatus == SyncStatusUi.SYNCED) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(2.dp)
            ) {
                Icon(Icons.Default.Cloud, contentDescription = "Синхронизировано", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(modifier: Modifier = Modifier, onGrantPermission: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Нужен доступ к фото", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Tweakly нужен доступ к галерее для отображения ваших фото", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrantPermission, shape = RoundedCornerShape(12.dp)) {
            Text("Разрешить доступ")
        }
    }
}

@Composable
private fun EmptyGalleryContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Фото не найдены", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("На устройстве нет фото или доступ ограничен", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
