package com.example.tweakly.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.model.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onMediaClick: (Long) -> Unit,
    vm: SearchViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SearchBar(query = query,
                        onQueryChange = { q -> query = q; if (q.length >= 2) vm.search(q) else vm.clearResults() },
                        active = false, onActiveChange = {},
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        placeholder = { Text("Поиск по имени файла...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (query.isNotEmpty()) {{ IconButton({ query = ""; vm.clearResults() }) { Icon(Icons.Default.Clear, null) } }} else null
                    ) {}
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.results.isEmpty() && query.length >= 2 -> {
                    Column(Modifier.fillMaxSize(), Alignment.CenterHorizontally, Arrangement.Center) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("Ничего не найдено по запросу \"$query\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                query.length < 2 -> {
                    Column(Modifier.fillMaxSize(), Alignment.CenterHorizontally, Arrangement.Center) {
                        Icon(Icons.Default.Search, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("Введите минимум 2 символа для поиска",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    Text("Найдено: ${state.results.size}",
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyVerticalGrid(columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(state.results, key = { it.id }) { item ->
                            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                                .clickable { onMediaClick(item.id) }) {
                                AsyncImage(model = item.uri, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (item.mediaType == MediaType.VIDEO) {
                                    Icon(Icons.Default.PlayCircle, null,
                                        Modifier.align(Alignment.Center).size(28.dp),
                                        tint = androidx.compose.ui.graphics.Color.White.copy(.8f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
