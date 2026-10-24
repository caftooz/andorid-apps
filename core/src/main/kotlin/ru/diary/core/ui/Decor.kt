package ru.diary.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.theme.mix

/**
 * Фон экрана: основной цвет темы, мягкие пятна фирменного света и едва заметная сетка точек вверху.
 * Рисуется один раз и кэшируется, поэтому не тратит батарею при прокрутке.
 */
@Composable
fun DiaryBackdrop(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.TopStart, content: @Composable BoxScope.() -> Unit) {
    val x = DiaryTheme.extras
    val bg = MaterialTheme.colorScheme.background
    val dot = MaterialTheme.colorScheme.onBackground
    Box(
        modifier.fillMaxSize().background(bg).drawWithCache {
            val w = size.width; val h = size.height
            val a = if (x.dark) 0.26f else 0.34f
            val c1 = Offset(w * 0.08f, h * 0.02f); val r1 = w * 0.95f
            val c2 = Offset(w * 1.02f, h * 0.30f); val r2 = w * 0.75f
            val c3 = Offset(w * 0.15f, h * 1.0f); val r3 = w * 0.9f
            val b1 = Brush.radialGradient(listOf(x.glowA.copy(alpha = a), Color.Transparent), c1, r1)
            val b2 = Brush.radialGradient(listOf(x.glowB.copy(alpha = a * 0.75f), Color.Transparent), c2, r2)
            val b3 = Brush.radialGradient(listOf(x.glowA.copy(alpha = a * 0.45f), Color.Transparent), c3, r3)
            val step = 22.dp.toPx(); val dr = 1.dp.toPx()
            val rows = (h * 0.32f / step).toInt()
            onDrawBehind {
                drawCircle(b1, r1, c1); drawCircle(b2, r2, c2); drawCircle(b3, r3, c3)
                for (j in 0..rows) {
                    val fade = 1f - j.toFloat() / (rows + 1)
                    var i = 0f
                    while (i < w) {
                        drawCircle(dot.copy(alpha = 0.07f * fade), dr, Offset(i + step / 2, j * step + step / 2)); i += step
                    }
                }
            }
        },
        contentAlignment = contentAlignment,
    ) { content() }
}

/** Четырёхлучевая звёздочка-искра для украшений. */
fun DrawScope.sparkle(center: Offset, r: Float, color: Color) {
    val k = r * 0.22f
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + k, center.y - k, center.x + r, center.y)
        quadraticTo(center.x + k, center.y + k, center.x, center.y + r)
        quadraticTo(center.x - k, center.y + k, center.x - r, center.y)
        quadraticTo(center.x - k, center.y - k, center.x, center.y - r)
        close()
    }
    drawPath(p, color)
}

/**
 * Шапка-баннер главных экранов: фирменный градиент, кольца, искры и крупная полупрозрачная иконка.
 * Под заголовком можно разместить свои элементы (кольцо прогресса, цифры дня).
 */
@Composable
fun HeroHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String = "star",
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val x = DiaryTheme.extras
    val on = x.onHero
    val shape = RoundedCornerShape(30.dp)
    val glow = x.hero.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val t = rememberInfiniteTransition(label = "hero")
    // Иконка медленно покачивается: шапка выглядит живой, а перерисовывается только маленький слой.
    val swing by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "swing")
    val twinkle by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "twinkle")
    Box(
        modifier.fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(x.heroBrush)
            .drawWithCache {
                val w = size.width; val h = size.height
                val ring = Stroke(1.5.dp.toPx())
                val shine = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), 0f, h * 0.6f)
                onDrawBehind {
                    drawRect(shine)
                    drawCircle(on.copy(alpha = 0.10f), h * 0.95f, Offset(w * 0.95f, h * 0.05f))
                    drawCircle(on.copy(alpha = 0.16f), h * 0.70f, Offset(w * 0.95f, h * 0.05f), style = ring)
                    drawCircle(on.copy(alpha = 0.10f), h * 1.15f, Offset(w * 0.95f, h * 0.05f), style = ring)
                    drawCircle(on.copy(alpha = 0.07f), h * 0.45f, Offset(w * 0.08f, h * 1.05f))
                }
            },
    ) {
        Canvas(Modifier.matchParentSize().graphicsLayer { alpha = twinkle }.clearAndSetSemantics { }) {
            // Искры стоят в полях баннера, чтобы не ложиться на текст.
            sparkle(Offset(size.width * 0.64f, 11.dp.toPx()), 6.dp.toPx(), on.copy(alpha = 0.85f))
            sparkle(Offset(size.width - 12.dp.toPx(), size.height * 0.78f), 4.dp.toPx(), on.copy(alpha = 0.6f))
            sparkle(Offset(size.width * 0.42f, size.height - 10.dp.toPx()), 3.dp.toPx(), on.copy(alpha = 0.5f))
        }
        Icon(
            DiaryIcons.get(icon), null, tint = on.copy(alpha = 0.26f),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-6).dp).size(128.dp).rotate(swing),
        )
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(30.dp).background(on.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(DiaryIcons.get(icon), null, tint = on, modifier = Modifier.size(17.dp))
                }
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.88f), maxLines = 2)
            }
            Text(
                title, style = MaterialTheme.typography.displaySmall, color = on, maxLines = 3,
                modifier = Modifier.padding(end = 56.dp).semantics { heading() },
            )
            if (content != null) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides on) { content() }
            }
        }
    }
}

/** Цифра на баннере: крупное значение и подпись под ним. */
@Composable
fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    val on = DiaryTheme.extras.onHero
    Column(
        modifier.background(on.copy(alpha = 0.16f), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = on, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.85f), maxLines = 1)
    }
}

/** Иконка на градиентной плашке с мягким цветным свечением. */
@Composable
fun IconBadge(icon: String, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.34f)
    Box(
        modifier.size(size).shadow(8.dp, shape, ambientColor = color, spotColor = color)
            .background(Brush.linearGradient(listOf(mix(color, Color.White, 0.22f), color, mix(color, Color.Black, 0.12f))), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DiaryIcons.get(icon), null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

/** Иконка в мягком цветном круге: для второстепенных мест, где плашка была бы слишком яркой. */
@Composable
fun IconBubble(icon: String, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).background(Brush.linearGradient(listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0.10f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DiaryIcons.get(icon), null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

/** Пустое состояние: иллюстрация из колец и иконки, под ней пояснение. */
@Composable
fun EmptyState(icon: String, text: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val x = DiaryTheme.extras
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val c = center
                drawCircle(Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.22f), Color.Transparent), c, size.minDimension / 2), size.minDimension / 2, c)
                drawCircle(cs.primary.copy(alpha = 0.25f), size.minDimension * 0.36f, c, style = Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f))))
                sparkle(Offset(size.width * 0.82f, size.height * 0.2f), 6.dp.toPx(), x.accent.copy(alpha = 0.8f))
                sparkle(Offset(size.width * 0.16f, size.height * 0.74f), 4.dp.toPx(), cs.primary.copy(alpha = 0.7f))
            }
            IconBadge(icon, cs.primary, size = 56.dp)
        }
        Muted(text, Modifier.padding(horizontal = 12.dp), textAlign = TextAlign.Center)
    }
}
