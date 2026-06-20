package com.telegramdrive.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController

// Brand fallback palette (used when dynamic color is off or on Android < 12)
private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramBlueDark = Color(0xFF0088CC)

private val LightColors = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE9FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = TelegramBlueDark,
    onSecondary = Color.White,
    background = Color(0xFFFAFBFC),
    onBackground = Color(0xFF101418),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101418),
    surfaceVariant = Color(0xFFE7F0F5),
    onSurfaceVariant = Color(0xFF2C3539),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD3FF),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004C6A),
    onPrimaryContainer = Color(0xFFBFE9FF),
    secondary = Color(0xFF8BD3FF),
    onSecondary = Color(0xFF00344A),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E3E7),
    surface = Color(0xFF171C20),
    onSurface = Color(0xFFE0E3E7),
    surfaceVariant = Color(0xFF2C3539),
    onSurfaceVariant = Color(0xFFBFC8CC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/**
 * App theme. Honours:
 *  - Dynamic color (Material You) on Android 12+ when enabled in prefs
 *  - Dark/light/system preference
 */
@Composable
fun TelegramDriveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
