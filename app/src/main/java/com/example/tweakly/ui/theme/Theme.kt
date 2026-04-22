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

// Brand colors extracted from logo (muted purple palette)
val Purple80  = Color(0xFFCBC2FF)
val Purple60  = Color(0xFF9D91E8)
val Purple40  = Color(0xFF6C63AC)
val Purple20  = Color(0xFF3D3A5C)
val PurpleDim = Color(0xFF4A4570)

private val DarkColors = darkColorScheme(
    primary           = Purple80,
    onPrimary         = Color(0xFF1A1236),
    primaryContainer  = Purple20,
    onPrimaryContainer= Purple80,
    secondary         = Color(0xFFCBC4DC),
    onSecondary       = Color(0xFF332D4F),
    background        = Color(0xFF0F0E1A),
    onBackground      = Color(0xFFE6E1F0),
    surface           = Color(0xFF1A1828),
    onSurface         = Color(0xFFE6E1F0),
    surfaceVariant    = Color(0xFF27243A),
    onSurfaceVariant  = Color(0xFFCAC4D8),
    outline           = Color(0xFF948FAD),
    error             = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary           = Purple40,
    onPrimary         = Color.White,
    primaryContainer  = Color(0xFFEAE4FF),
    onPrimaryContainer= Purple20,
    secondary         = Color(0xFF615B79),
    onSecondary       = Color.White,
    background        = Color(0xFFFFFBFF),
    onBackground      = Color(0xFF1C1B2E),
    surface           = Color(0xFFFFFBFF),
    onSurface         = Color(0xFF1C1B2E),
    surfaceVariant    = Color(0xFFE6E0F0),
    onSurfaceVariant  = Color(0xFF48454D),
    outline           = Color(0xFF79757F),
    error             = Color(0xFFBA1A1A)
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
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}
