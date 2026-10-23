package ru.diary.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Режим темы из настроек: светлая, тёмная или как в системе. */
enum class ThemeMode { System, Light, Dark }

private val StaticLight = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    secondary = Color(0xFF8A5A00),
    tertiary = Color(0xFF3F5BA9),
)
private val StaticDark = darkColorScheme(
    primary = Color(0xFF7FD8BF),
    secondary = Color(0xFFFFB955),
    tertiary = Color(0xFFB0C6FF),
)

@Composable
fun DiaryTheme(
    mode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> StaticDark
        else -> StaticLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
