package com.example.tweakly.ui.settings

import androidx.compose.animation.*
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
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showLogout by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── User card ────────────────────────────────────────────────────
            state.user?.let { user ->
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center) {
                            Text(
                                (user.displayName?.firstOrNull() ?: user.email?.firstOrNull() ?: '?')
                                    .uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(user.displayName ?: "Пользователь", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge)
                            user.email?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } ?: run {
                // Guest mode
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Гостевой режим", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Server status ─────────────────────────────────────────────────
            SettingsCard(title = "Сервер") {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, color, text) = when (state.serverOnline) {
                            true  -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Онлайн")
                            false -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Недоступен")
                            null  -> Triple(Icons.Default.Circle, MaterialTheme.colorScheme.onSurfaceVariant, "Проверяю...")
                        }
                        Icon(icon, null, Modifier.size(18.dp), tint = color)
                        Spacer(Modifier.width(8.dp))
                        Text(text)
                    }
                    TextButton({ vm.checkServer() }) { Text("Проверить") }
                }
            }

            // ── Repo ──────────────────────────────────────────────────────────
            if (state.user != null) {
                SettingsCard(title = "Репозиторий GitHub") {
                    if (state.repoExists && state.repoName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(state.repoName!!, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        }
                        state.repoUrl?.let { url ->
                            Spacer(Modifier.height(4.dp))
                            TextButton({ uriHandler.openUri(url) }, contentPadding = PaddingValues(0.dp)) {
                                Text("Открыть на GitHub", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text("Репозиторий не создан", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.createRepo() },
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

            // ── Sync settings ─────────────────────────────────────────────────
            SettingsCard(title = "Синхронизация") {
                SwitchRow(
                    icon = Icons.Default.Wifi,
                    label = "Только по Wi-Fi",
                    sub = "Не использовать мобильный интернет",
                    checked = state.settings.wifiOnly,
                    onChecked = { vm.setWifiOnly(it) }
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(
                    icon = Icons.Default.Sync,
                    label = "Авто-синхронизация",
                    sub = "Загружать новые файлы автоматически",
                    checked = state.settings.autoSync,
                    onChecked = { vm.setAutoSync(it) }
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                // Quality slider
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HighQuality, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Качество загрузки", style = MaterialTheme.typography.bodyMedium)
                            Text("${state.settings.uploadQuality}% JPEG",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Slider(
                    value = state.settings.uploadQuality.toFloat(),
                    onValueChange = { vm.setUploadQuality(it.toInt()) },
                    valueRange = 30f..100f, steps = 13,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsCard(title = "О приложении") {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Версия", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("2.0.0")
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Бэкенд", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("tweaklybot-server.onrender.com", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Logout button ─────────────────────────────────────────────────
            if (state.user != null) {
                Button(
                    onClick = { showLogout = true },
                    Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(Icons.Default.Logout, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("Выйти из аккаунта",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            icon = { Icon(Icons.Default.Logout, null) },
            title = { Text("Выйти?") },
            text = { Text("Вы выйдете из аккаунта. Локальные данные сохранятся.") },
            confirmButton = {
                TextButton({ vm.signOut(); onLogout() }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton({ showLogout = false }) { Text("Отмена") } }
        )
    }
}

// ── Reusable components ────────────────────────────────────────────────────

@Composable
private fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(.5f))) {
        Column(Modifier.padding(16.dp)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector, label: String, sub: String? = null,
    checked: Boolean, onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                sub?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
