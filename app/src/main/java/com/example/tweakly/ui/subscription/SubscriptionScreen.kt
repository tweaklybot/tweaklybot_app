package com.example.tweakly.ui.subscription

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    vm: SubscriptionViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Premium") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(.3f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                // Crown icon
                Box(
                    Modifier.size(80.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, null, Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.height(16.dp))

                Text("Tweakly Premium", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("Разблокируйте все возможности", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(24.dp))

                // Current storage (free tier)
                if (!state.isPremium) {
                    StorageMeter(
                        usedPercent = state.storagePercent,
                        usedGB = state.usedStorageGB,
                        limitGB = 1
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // Plan comparison cards
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlanCard(
                        modifier = Modifier.weight(1f),
                        name = "Free",
                        price = "0 ₽",
                        isSelected = !state.isPremium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        features = listOf(
                            "1 ГБ хранилища" to true,
                            "Только WiFi" to true,
                            "Качество 60%" to true,
                            "Только фото" to true,
                            "10 OCR в день" to true,
                            "Базовый редактор" to true
                        )
                    )
                    PlanCard(
                        modifier = Modifier.weight(1f),
                        name = "Premium",
                        price = "299 ₽/мес",
                        isSelected = state.isPremium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        features = listOf(
                            "∞ хранилище" to true,
                            "Любая сеть" to true,
                            "100% качество" to true,
                            "Фото + видео" to true,
                            "OCR без лимитов" to true,
                            "ИИ-редактор" to true
                        ),
                        badge = "ЛУЧШИЙ ВЫБОР"
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Feature list detail
                FeatureList()

                Spacer(Modifier.height(24.dp))

                // Action button
                if (state.isPremium) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Premium активен", fontWeight = FontWeight.Bold)
                                if (state.expiresAt > 0) {
                                    Text("Истекает: ${state.expireDate}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Бессрочная подписка",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { vm.subscribe() },
                        Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !state.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                        } else {
                            Icon(Icons.Default.Star, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Получить Premium — 299 ₽/мес",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Demo button for testing
                    OutlinedButton(
                        onClick = { vm.activateDemoPremium() },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Science, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Демо Premium (тест, 7 дней)")
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Отменить можно в любое время",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StorageMeter(usedPercent: Float, usedGB: Double, limitGB: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Облачное хранилище", fontWeight = FontWeight.SemiBold)
                Text("%.2f / %d ГБ".format(usedGB, limitGB),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { usedPercent },
                modifier = Modifier.fillMaxWidth(),
                color = if (usedPercent > .8f) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
            )
            if (usedPercent > .8f) {
                Spacer(Modifier.height(4.dp))
                Text("Хранилище почти заполнено! Перейдите на Premium",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PlanCard(
    modifier: Modifier,
    name: String,
    price: String,
    isSelected: Boolean,
    color: Color,
    features: List<Pair<String, Boolean>>,
    badge: String? = null
) {
    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = border,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(Modifier.padding(12.dp)) {
            badge?.let {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary).padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center) {
                    Text(it, color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(name, fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium)
            Text(price, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            features.forEach { (label, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun FeatureList() {
    val features = listOf(
        Icons.Default.CloudUpload  to "Безлимитное облачное хранилище в GitHub",
        Icons.Default.Wifi         to "Загрузка на любой сети, не только WiFi",
        Icons.Default.Videocam     to "Загрузка видео до 500 МБ",
        Icons.Default.AutoFixHigh  to "Полный редактор с ИИ-функциями",
        Icons.Default.TextFields   to "OCR без ограничений — любое количество",
        Icons.Default.QrCode       to "QR-сканер без лимитов",
        Icons.Default.HighQuality  to "Оригинальное качество загрузки 100%",
        Icons.Default.SupportAgent to "Приоритетная поддержка"
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        features.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
