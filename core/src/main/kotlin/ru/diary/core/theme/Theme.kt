package ru.diary.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.diary.core.R

/** Режим темы из настроек: светлая, тёмная или как в системе. */
enum class ThemeMode { System, Light, Dark }

/** Статичная палитра приложения. Нужна как запасной вариант без Material You и для цветов категорий. */
enum class Palette { Training, Nutrition }

/**
 * Цвета, которых нет в Material: категории занятий, белок, предупреждение. Берутся из палитры прототипа
 * и подбираются отдельно для светлой и тёмной темы, чтобы сохранялся контраст.
 */
@Immutable
data class DiaryExtras(
    val volley: Color,
    val friends: Color,
    val cardio: Color,
    val home: Color,
    val protein: Color,
    val warn: Color,
    val danger: Color,
    val line: Color,
)

val LocalDiaryExtras = staticCompositionLocalOf {
    DiaryExtras(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

/** Доступ к дополнительным цветам: `DiaryTheme.extras`. */
object DiaryTheme {
    val extras: DiaryExtras
        @Composable @ReadOnlyComposable get() = LocalDiaryExtras.current
}

private val LightExtras = DiaryExtras(
    volley = Color(0xFFC2570C), friends = Color(0xFF5F6F82), cardio = Color(0xFF1E8F63), home = Color(0xFF7A4FD0),
    protein = Color(0xFFB26B00), warn = Color(0xFFA8530A), danger = Color(0xFFB3261E), line = Color(0xFFD2D9E4),
)
private val DarkExtras = DiaryExtras(
    volley = Color(0xFFF0A060), friends = Color(0xFFA5B3C4), cardio = Color(0xFF4FD1A0), home = Color(0xFFB79CFF),
    protein = Color(0xFFE3A83F), warn = Color(0xFFE0A04A), danger = Color(0xFFF0877B), line = Color(0xFF2A3547),
)

private fun staticScheme(palette: Palette, dark: Boolean): ColorScheme = when (palette) {
    Palette.Training -> if (dark) darkColorScheme(
        primary = Color(0xFF7BA6FF), onPrimary = Color(0xFF0A1B40), background = Color(0xFF0E131B), onBackground = Color(0xFFE6ECF5),
        surface = Color(0xFF151C27), onSurface = Color(0xFFE6ECF5), surfaceVariant = Color(0xFF1F2836), onSurfaceVariant = Color(0xFF98A4B8),
        outline = Color(0xFF2A3547), error = Color(0xFFF0877B),
    ) else lightColorScheme(
        primary = Color(0xFF1E58C8), onPrimary = Color.White, background = Color(0xFFEEF1F6), onBackground = Color(0xFF121A28),
        surface = Color.White, onSurface = Color(0xFF121A28), surfaceVariant = Color(0xFFE3E8F0), onSurfaceVariant = Color(0xFF566277),
        outline = Color(0xFFD2D9E4), error = Color(0xFFB3261E),
    )
    Palette.Nutrition -> if (dark) darkColorScheme(
        primary = Color(0xFF5CC796), onPrimary = Color(0xFF05210F), background = Color(0xFF0F1511), onBackground = Color(0xFFE7EEE8),
        surface = Color(0xFF17201A), onSurface = Color(0xFFE7EEE8), surfaceVariant = Color(0xFF212C25), onSurfaceVariant = Color(0xFF9BAA9F),
        outline = Color(0xFF2B382F), error = Color(0xFFF0877B),
    ) else lightColorScheme(
        primary = Color(0xFF1F6E4E), onPrimary = Color.White, background = Color(0xFFF1F4EF), onBackground = Color(0xFF16211A),
        surface = Color.White, onSurface = Color(0xFF16211A), surfaceVariant = Color(0xFFE6ECE3), onSurfaceVariant = Color(0xFF55655A),
        outline = Color(0xFFD3DBD0), error = Color(0xFFB3261E),
    )
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Onest для текста, Unbounded для заголовков (как в веб-версии). */
val OnestFamily = FontFamily(variable(R.font.onest, 400), variable(R.font.onest, 500), variable(R.font.onest, 600), variable(R.font.onest, 700))
val UnboundedFamily = FontFamily(variable(R.font.unbounded, 600), variable(R.font.unbounded, 700))

private val base = Typography()
private fun TextStyle.onest() = copy(fontFamily = OnestFamily)

val DiaryTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = UnboundedFamily),
    displayMedium = base.displayMedium.copy(fontFamily = UnboundedFamily),
    displaySmall = base.displaySmall.copy(fontFamily = UnboundedFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = base.titleLarge.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = base.titleMedium.onest().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.onest().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.onest(),
    bodyMedium = base.bodyMedium.onest(),
    bodySmall = base.bodySmall.onest(),
    labelLarge = base.labelLarge.onest().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.onest(),
    labelSmall = base.labelSmall.onest(),
)

/**
 * Тема приложения. На Android 12+ по умолчанию используются динамические цвета (Material You);
 * на более старых версиях и при выключенных динамических цветах — статичная палитра приложения.
 */
@Composable
fun DiaryTheme(
    mode: ThemeMode = ThemeMode.System,
    palette: Palette = Palette.Training,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else staticScheme(palette, dark)
    CompositionLocalProvider(LocalDiaryExtras provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = scheme, typography = DiaryTypography, content = content)
    }
}
