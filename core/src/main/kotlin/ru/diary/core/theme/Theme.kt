package ru.diary.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
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

/** Фирменная палитра приложения: «Пульс» для тренировок и «Свежесть» для рациона. */
enum class Palette { Training, Nutrition }

/**
 * Цвета, которых нет в Material: категории занятий, белок, предупреждение, а также фирменные градиенты
 * (шапки, кнопки, графики) и мягкие пятна света на фоне. Подбираются отдельно для светлой и тёмной темы.
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
    /** Второй фирменный цвет: конец градиентов. */
    val accent: Color = Color.Unspecified,
    /** Цвета шапок-баннеров слева направо. */
    val hero: List<Color> = emptyList(),
    /** Пятна света на фоне экрана. */
    val glowA: Color = Color.Unspecified,
    val glowB: Color = Color.Unspecified,
    /** Стеклянная подложка карточек. */
    val glass: Color = Color.Unspecified,
    /** Светлый блик на краю карточек. */
    val rim: Color = Color.Unspecified,
    /** Текст и значки поверх шапок-баннеров. */
    val onHero: Color = Color.White,
    /** Градиент главных кнопок и выбранных пунктов, и цвет текста на нём. */
    val button: List<Color> = emptyList(),
    val onButton: Color = Color.White,
    val dark: Boolean = false,
) {
    /** Основной градиент: кнопки, выбранные пункты, линии графиков. */
    fun brush(start: Color, end: Color = accent): Brush = Brush.linearGradient(listOf(start, end))
    val heroBrush: Brush get() = Brush.linearGradient(hero.ifEmpty { listOf(accent, accent) })
    val buttonBrush: Brush get() = Brush.linearGradient(button.ifEmpty { listOf(accent, accent) })
}

val LocalDiaryExtras = staticCompositionLocalOf {
    DiaryExtras(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

/** Доступ к дополнительным цветам: `DiaryTheme.extras`. */
object DiaryTheme {
    val extras: DiaryExtras
        @Composable @ReadOnlyComposable get() = LocalDiaryExtras.current
}

private val LightCats = DiaryExtras(
    volley = Color(0xFFE2620F), friends = Color(0xFF5B6B86), cardio = Color(0xFF0E9E6E), home = Color(0xFF8B4FE0),
    protein = Color(0xFFC77700), warn = Color(0xFFB45A06), danger = Color(0xFFD02F4A), line = Color(0xFFDCDDEA),
)
private val DarkCats = DiaryExtras(
    volley = Color(0xFFFF9F5A), friends = Color(0xFFAEBBD2), cardio = Color(0xFF45E0A8), home = Color(0xFFC3A2FF),
    protein = Color(0xFFFFC14D), warn = Color(0xFFFFB35C), danger = Color(0xFFFF7A90), line = Color(0xFF2A2A44),
)

/** Набор цветов одной палитры для одной темы. */
private class Swatch(
    val primary: Color, val onPrimary: Color, val accent: Color, val hero: List<Color>,
    val bg: Color, val surface: Color, val ink: Color, val muted: Color, val outline: Color,
    val glowA: Color, val glowB: Color,
)

private fun swatch(palette: Palette, dark: Boolean): Swatch = when (palette) {
    Palette.Training -> if (dark) Swatch(
        primary = Color(0xFF9C97FF), onPrimary = Color(0xFF120E3D), accent = Color(0xFFFF7EA6),
        hero = listOf(Color(0xFF5B4CF5), Color(0xFFA43FE6), Color(0xFFFF5C8A)),
        bg = Color(0xFF0A0A16), surface = Color(0xFF15152A), ink = Color(0xFFECEBFF), muted = Color(0xFFA2A1C4), outline = Color(0xFF2E2D4D),
        glowA = Color(0xFF5B4CF5), glowB = Color(0xFFFF4F86),
    ) else Swatch(
        primary = Color(0xFF5546F0), onPrimary = Color.White, accent = Color(0xFFE8457A),
        hero = listOf(Color(0xFF5B4CF5), Color(0xFF9B3FE6), Color(0xFFFF5C8A)),
        bg = Color(0xFFF5F4FD), surface = Color.White, ink = Color(0xFF16152E), muted = Color(0xFF615F86), outline = Color(0xFFDCDAF0),
        glowA = Color(0xFF8C80FF), glowB = Color(0xFFFF8FB2),
    )
    Palette.Nutrition -> if (dark) Swatch(
        primary = Color(0xFF45DF9C), onPrimary = Color(0xFF00281A), accent = Color(0xFFFFC65C),
        hero = listOf(Color(0xFF0B9A6A), Color(0xFF3FC07A), Color(0xFFF2B33D)),
        bg = Color(0xFF07110D), surface = Color(0xFF111E18), ink = Color(0xFFE6F4EC), muted = Color(0xFF97B1A3), outline = Color(0xFF223A2F),
        glowA = Color(0xFF13B07A), glowB = Color(0xFFF2B33D),
    ) else Swatch(
        primary = Color(0xFF0B8A5E), onPrimary = Color.White, accent = Color(0xFFE09A12),
        hero = listOf(Color(0xFF0B9A6A), Color(0xFF3DBE76), Color(0xFFF2B33D)),
        bg = Color(0xFFF3F8F2), surface = Color.White, ink = Color(0xFF102119), muted = Color(0xFF52695C), outline = Color(0xFFD4E3D8),
        glowA = Color(0xFF6FE0AE), glowB = Color(0xFFFFD27A),
    )
}

/** Смешение двух цветов: `t = 0` — первый, `t = 1` — второй. */
fun mix(a: Color, b: Color, t: Float): Color = lerp(a, b, t)

private fun staticScheme(s: Swatch, dark: Boolean): ColorScheme {
    val container = mix(s.surface, s.primary, if (dark) 0.28f else 0.14f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = s.primary, onPrimary = s.onPrimary,
        primaryContainer = container, onPrimaryContainer = if (dark) s.ink else mix(s.primary, Color.Black, 0.45f),
        secondary = s.accent, onSecondary = Color.White,
        secondaryContainer = mix(s.surface, s.accent, if (dark) 0.26f else 0.16f), onSecondaryContainer = s.ink,
        tertiary = s.accent, onTertiary = Color.White,
        tertiaryContainer = mix(s.surface, s.accent, if (dark) 0.26f else 0.16f), onTertiaryContainer = s.ink,
        background = s.bg, onBackground = s.ink,
        surface = s.surface, onSurface = s.ink,
        surfaceVariant = mix(s.surface, s.outline, 0.6f), onSurfaceVariant = s.muted,
        surfaceTint = s.primary,
        surfaceBright = mix(s.surface, Color.White, if (dark) 0.06f else 0f),
        surfaceDim = mix(s.bg, s.outline, 0.3f),
        surfaceContainerLowest = if (dark) mix(s.bg, s.surface, 0.5f) else Color.White,
        surfaceContainerLow = mix(s.surface, s.bg, if (dark) 0.25f else 0.35f),
        surfaceContainer = s.surface,
        surfaceContainerHigh = mix(s.surface, s.primary, if (dark) 0.07f else 0.04f),
        surfaceContainerHighest = mix(s.surface, s.primary, if (dark) 0.12f else 0.07f),
        inverseSurface = s.ink, inverseOnSurface = s.bg, inversePrimary = s.accent,
        outline = s.outline, outlineVariant = mix(s.outline, s.surface, 0.35f),
        error = if (dark) DarkCats.danger else LightCats.danger,
        scrim = Color.Black,
    )
}

private fun extrasFor(s: Swatch, dark: Boolean) = (if (dark) DarkCats else LightCats).copy(
    accent = s.accent, hero = s.hero, glowA = s.glowA, glowB = s.glowB,
    button = listOf(s.hero[0], mix(s.hero[0], s.hero[1], 0.75f)), onButton = Color.White, onHero = Color.White,
    // В светлой теме карточки непрозрачные: так тень под ними не просвечивает сквозь стекло.
    glass = if (dark) s.surface.copy(alpha = 0.80f) else s.surface,
    rim = Color.White.copy(alpha = if (dark) 0.14f else 0.9f),
    dark = dark,
)

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
    displaySmall = base.displaySmall.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = base.titleLarge.copy(fontFamily = UnboundedFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
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
 * Тема приложения. По умолчанию — фирменная палитра со своими градиентами и стеклянными карточками;
 * на Android 12+ по желанию можно взять цвета обоев (Material You), фирменные элементы тогда перекрашиваются под них.
 */
@Composable
fun DiaryTheme(
    mode: ThemeMode = ThemeMode.System,
    palette: Palette = Palette.Training,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val sw = swatch(palette, dark)
    var scheme = staticScheme(sw, dark)
    var extras = extrasFor(sw, dark)
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        extras = extras.copy(
            accent = scheme.tertiary,
            hero = listOf(scheme.primaryContainer, mix(scheme.primaryContainer, scheme.tertiaryContainer, 0.5f), scheme.tertiaryContainer),
            onHero = scheme.onPrimaryContainer,
            button = listOf(scheme.primary, mix(scheme.primary, scheme.tertiary, 0.4f)), onButton = scheme.onPrimary,
            glowA = scheme.primary, glowB = scheme.tertiary,
            glass = if (dark) scheme.surfaceContainer.copy(alpha = 0.82f) else scheme.surfaceContainerLowest,
        )
    }
    CompositionLocalProvider(LocalDiaryExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = DiaryTypography, shapes = DiaryShapes) {
            // Стеклянные и прозрачные подложки не входят в схему Material, поэтому цвет текста на них задаётся явно.
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}

/** Скругления: мягкие, крупнее стандартных. */
val DiaryShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp),
)
