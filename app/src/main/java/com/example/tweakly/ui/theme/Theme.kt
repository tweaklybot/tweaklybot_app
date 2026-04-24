package com.example.tweakly.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Brand palette (from logo — purple/slate) ──────────────────────────────────
private val Brand80  = Color(0xFFCBC2FF)
private val Brand60  = Color(0xFF9D91E8)
private val Brand40  = Color(0xFF6C63AC)
private val Brand20  = Color(0xFF3D3A5C)

// ── TRUE BLACK dark theme — saves OLED battery, makes photos pop ──────────────
private val TrueBlackDark = darkColorScheme(
    primary            = Brand80,
    onPrimary          = Color(0xFF1A1236),
    primaryContainer   = Brand20,
    onPrimaryContainer = Brand80,
    secondary          = Color(0xFFCBC4DC),
    onSecondary        = Color(0xFF332D4F),
    tertiary           = Color(0xFFEFB8C8),
    background         = Color(0xFF000000),   // ← true black
    onBackground       = Color(0xFFE6E1F0),
    surface            = Color(0xFF111111),   // near-black surface
    onSurface          = Color(0xFFE6E1F0),
    surfaceVariant     = Color(0xFF1E1C2A),
    onSurfaceVariant   = Color(0xFFCAC4D8),
    outline            = Color(0xFF948FAD),
    error              = Color(0xFFFFB4AB),
    onError            = Color(0xFF690005)
)

private val LightColors = lightColorScheme(
    primary            = Brand40,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFEAE4FF),
    onPrimaryContainer = Brand20,
    secondary          = Color(0xFF615B79),
    onSecondary        = Color.White,
    background         = Color(0xFFFFFBFF),
    onBackground       = Color(0xFF1C1B2E),
    surface            = Color(0xFFFFFBFF),
    onSurface          = Color(0xFF1C1B2E),
    surfaceVariant     = Color(0xFFE6E0F0),
    onSurfaceVariant   = Color(0xFF48454D),
    outline            = Color(0xFF79757F),
    error              = Color(0xFFBA1A1A)
)

@Composable
fun TweaklyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            // Use Material You dynamic colors but override background to true black in dark
            val dynamic = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            if (darkTheme) dynamic.copy(background = Color(0xFF000000), surface = Color(0xFF111111))
            else dynamic
        }
        darkTheme -> TrueBlackDark
        else      -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}
