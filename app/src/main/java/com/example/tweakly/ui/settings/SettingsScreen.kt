package com.example.tweakly.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSubscribeClick: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showLogout by remember { mutableStateOf(false) }
    var showCreateRepo by remember { mutableStateOf(false) }
    var repoNameInput by remember { mutableStateOf("tweakly-photos") }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Account ────────────────────────────────────────────────────
            SettingsCard {
                if (state.user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center) {
                            Text(
                                (state.user!!.displayName?.firstOrNull()
                                    ?: state.user!!.email?.firstOrNull() ?: '?')
                                    .uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(state.user!!.displayName ?: "Пользователь",
                                fontWeight = FontWeight.SemiBold)
                            state.user!!.email?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Гостевой режим", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Premium banner ─────────────────────────────────────────────
            if (!state.isPremium) {
                Card(Modifier.fillMaxWidth().clickable(onClick = onSubscribeClick),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Попробуйте Premium", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Text("Безлимитное хранилище, видео, ИИ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Icon(Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Premium активен", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── Server ─────────────────────────────────────────────────────
            SettingsCard(title = "Сервер") {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, color, text) = when (state.serverOnline) {
                            true  -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Онлайн")
                            false -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Недоступен")
                            null  -> Triple(Icons.Default.Refresh, MaterialTheme.colorScheme.onSurfaceVariant, "Проверка...")
                        }
                        Icon(icon, null, Modifier.size(18.dp), tint = color)
                        Spacer(Modifier.width(8.dp))
                        Text(text)
                    }
                    TextButton({ vm.checkServer() }) { Text("Проверить") }
                }
            }

            // ── Repo ───────────────────────────────────────────────────────
            if (state.user != null) {
                SettingsCard(title = "GitHub Репозиторий") {
                    if (state.repoExists && state.repoName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(state.repoName!!, fontWeight = FontWeight.Medium)
                        }
                        state.repoUrl?.let {
                            TextButton({ uriHandler.openUri(it) },
                                contentPadding = PaddingValues(0.dp)) {
                                Text("Открыть на GitHub",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text("Репозиторий не создан",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showCreateRepo = true },
                            Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            enabled = !state.isCreatingRepo
                        ) {
                            if (state.isCreatingRepo) {
                                CircularProgressIndicator(Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Создать репозиторий")
                        }
                    }
                }
            }

            // ── Sync ───────────────────────────────────────────────────────
            SettingsCard(title = "Синхронизация") {
                SwitchRow(icon = Icons.Default.Wifi, label = "Только по Wi-Fi",
                    sub = if (state.isPremium) "Можно отключить в Premium"
                          else "Бесплатный план: только Wi-Fi",
                    checked = state.settings.wifiOnly,
                    enabled = state.isPremium,
                    onChecked = { vm.setWifiOnly(it) })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(icon = Icons.Default.Sync, label = "Авто-синхронизация",
                    sub = "Загружать новые файлы автоматически",
                    checked = state.settings.autoSync,
                    onChecked = { vm.setAutoSync(it) })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HighQuality, null, Modifier.size(20.dp),
                            tint = if (state.isPremium) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Качество загрузки")
                            Text(
                                if (state.isPremium) "${state.settings.uploadQuality}% — изменяется"
                                else "60% — только Premium",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!state.isPremium) {
                        Icon(Icons.Default.Lock, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.isPremium) {
                    Slider(value = state.settings.uploadQuality.toFloat(),
                        onValueChange = { vm.setUploadQuality(it.toInt()) },
                        valueRange = 30f..100f, steps = 13,
                        modifier = Modifier.fillMaxWidth())
                }
            }

            // ── About ──────────────────────────────────────────────────────
            SettingsCard(title = "О приложении") {
                InfoRow("Версия", "2.0.0")
                InfoRow("Сервер", "tweaklybot-server-1.onrender.com")
                InfoRow("Хранилище", "GitHub (приватный репозиторий)")
            }

            Spacer(Modifier.height(8.dp))

            if (state.user != null) {
                Button(onClick = { showLogout = true },
                    Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Icon(Icons.Default.Logout, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("Выйти из аккаунта",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLogout) {
        AlertDialog(onDismissRequest = { showLogout = false },
            icon = { Icon(Icons.Default.Logout, null) },
            title = { Text("Выйти?") },
            text = { Text("Вы выйдете из аккаунта. Локальные данные сохранятся.") },
            confirmButton = {
                TextButton({ vm.signOut(); onLogout() }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton({ showLogout = false }) { Text("Отмена") } })
    }

    if (showCreateRepo) {
        AlertDialog(onDismissRequest = { showCreateRepo = false },
            title = { Text("Создать репозиторий") },
            text = {
                OutlinedTextField(value = repoNameInput,
                    onValueChange = { repoNameInput = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp))
            },
            confirmButton = {
                TextButton({ vm.createRepo(repoNameInput); showCreateRepo = false }) {
                    Text("Создать")
                }
            },
            dismissButton = { TextButton({ showCreateRepo = false }) { Text("Отмена") } })
    }
}

@Composable private fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(.5f))) {
        Column(Modifier.padding(16.dp)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable private fun SwitchRow(icon: ImageVector, label: String, sub: String? = null,
    checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                sub?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
