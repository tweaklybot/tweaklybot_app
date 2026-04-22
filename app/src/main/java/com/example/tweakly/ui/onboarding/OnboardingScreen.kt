package com.example.tweakly.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
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
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tweakly.data.repository.SettingsRepository
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val color: Color, val title: String, val desc: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    settings: SettingsRepository = hiltViewModel<OnboardingViewModel>().settings
) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { 4 }

    val pages = listOf(
        Page(Icons.Default.PhotoLibrary, Color(0xFF6C63AC),
            "Умная Галерея", "Все ваши фото и видео в одном месте, сгруппированные по датам и категориям"),
        Page(Icons.Default.CloudUpload, Color(0xFF4CAF93),
            "Облачная Синхронизация", "Автоматическое резервное копирование в приватный GitHub-репозиторий"),
        Page(Icons.Default.AutoFixHigh, Color(0xFFE07B39),
            "Мощный Редактор", "Фильтры, обрезка, рисование, текст — полноценный редактор прямо в приложении"),
        Page(Icons.Default.Psychology, Color(0xFFD44EAD),
            "ИИ-функции", "Распознавание текста на фото, сканирование QR-кодов, группировка по лицам")
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { idx ->
            val page = pages[idx]
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon bubble
                Box(
                    Modifier.size(140.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(page.color.copy(.25f), page.color.copy(.05f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(100.dp).clip(CircleShape).background(page.color.copy(.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(page.icon, null, Modifier.size(52.dp), tint = page.color)
                    }
                }
                Spacer(Modifier.height(40.dp))
                Text(page.title, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(page.desc, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, lineHeight = 24.sp)
            }
        }

        // Skip button top-right
        TextButton(onClick = {
            scope.launch {
                settings.setSkipOnboarding(true)
                onSkip()
            }
        }, Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp)
            .statusBarsPadding()) {
            Text("Пропустить", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Bottom area
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 40.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i ->
                    val w by animateDpAsState(if (pager.currentPage == i) 28.dp else 8.dp, tween(200), label = "dot")
                    Box(
                        Modifier.height(8.dp).width(w).clip(CircleShape)
                            .background(
                                if (pager.currentPage == i) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(.2f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            val isLast = pager.currentPage == 3
            Button(
                onClick = {
                    scope.launch {
                        if (isLast) {
                            settings.setSkipOnboarding(true)
                            onContinue()
                        } else {
                            pager.animateScrollToPage(pager.currentPage + 1)
                        }
                    }
                },
                Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pages[pager.currentPage].color
                )
            ) {
                Text(if (isLast) "Начать" else "Далее",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
