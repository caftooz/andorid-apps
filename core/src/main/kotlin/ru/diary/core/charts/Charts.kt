package ru.diary.core.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.diary.core.util.Js
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Прогресс появления графика 0..1: запускается при первом показе и при смене данных. */
@Composable
private fun revealProgress(key: Any?): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key) { a.snapTo(0f); a.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
    return a.value
}

data class ChartPoint(val label: String, val value: Double)

/** Часть столбца на столбчатой диаграмме. */
data class BarPart(val value: Double, val color: Color)

data class BarColumn(val label: String, val parts: List<BarPart>) {
    val total: Double get() = parts.sumOf { it.value }
}

/** Подпись легенды: цветной кружок и текст. */
data class LegendItem(val label: String, val color: Color)

private fun DrawScope.text(
    m: TextMeasurer, s: String, x: Float, y: Float, style: TextStyle, anchor: Anchor = Anchor.Start, centerY: Boolean = true,
) {
    val r = m.measure(s, style)
    val left = when (anchor) { Anchor.Start -> x; Anchor.Center -> x - r.size.width / 2f; Anchor.End -> x - r.size.width }
    drawText(r, topLeft = Offset(left, if (centerY) y - r.size.height / 2f else y))
}

private enum class Anchor { Start, Center, End }

/** Округление шага сетки вверх до 1, 2, 5, 10 × 10^n (`niceStepUp` прототипа). */
fun niceStepUp(r: Double): Double {
    if (r <= 0) return 1.0
    val p = 10.0.pow(floor(log10(r)))
    val f = r / p
    return (if (f <= 1) 1.0 else if (f <= 2) 2.0 else if (f <= 5) 5.0 else 10.0) * p
}

/**
 * Линейный график с сеткой из трёх горизонталей, подписанными осями, пунктирной линией цели
 * и значением последней точки. Описание для TalkBack обязательно.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    unit: String,
    description: String,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 180.dp,
) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(color = muted, fontSize = 11.sp)
    val valueStyle = TextStyle(color = ink, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    val reveal = revealProgress(points)
    val full = "$description: от ${Js.fmt(points.first().value)} до ${Js.fmt(points.last().value)} $unit" +
        (goal?.let { ", цель ${Js.fmt(it)}" } ?: "")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = full; role = Role.Image },
    ) {
        val left = 44.dp.toPx(); val right = 16.dp.toPx(); val top = 20.dp.toPx(); val bottom = 28.dp.toPx()
        val w = size.width; val h = size.height
        val vals = points.map { it.value }
        val all = if (goal != null) vals + goal else vals
        val mn = all.min(); val mx = all.max(); val span = mx - mn
        val lo = max(0.0, floor(mn - (if (span == 0.0) 2.0 else span) * 0.4))
        var hi = ceil(mx + (if (span == 0.0) 2.0 else span) * 0.4)
        if (hi <= lo) hi = lo + 1
        val n = points.size
        fun xs(i: Int) = if (n == 1) left + (w - left - right) / 2 else left + (w - left - right) * i / (n - 1)
        fun ys(v: Double) = (top + (h - top - bottom) * (1 - (v - lo) / (hi - lo))).toFloat()
        for (t in listOf(lo, (lo + hi) / 2, hi)) {
            drawLine(grid, Offset(left, ys(t)), Offset(w - right, ys(t)), strokeWidth = 1.dp.toPx())
            text(measurer, Js.fmt(t), left - 6.dp.toPx(), ys(t), labelStyle, Anchor.End)
        }
        if (goal != null) {
            drawLine(
                muted, Offset(left, ys(goal)), Offset(w - right, ys(goal)), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
            text(measurer, "цель ${Js.fmt(goal)}", w - right, ys(goal) - 9.dp.toPx(), labelStyle, Anchor.End)
        }
        if (n > 1) {
            val line = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(xs(i), ys(p.value)) else lineTo(xs(i), ys(p.value)) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(xs(n - 1), ys(lo.toDouble())); lineTo(xs(0), ys(lo.toDouble())); close()
            }
            clipRect(right = left + (w - left) * reveal + 8.dp.toPx()) {
                drawPath(area, color.copy(alpha = 0.12f))
                drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        points.forEachIndexed { i, p ->
            val last = i == n - 1
            if (n == 1 || xs(i) <= left + (w - left) * reveal) drawCircle(color, if (last) 5.dp.toPx() else 3.dp.toPx(), Offset(xs(i), ys(p.value)))
        }
        val lastX = xs(n - 1).coerceIn(left + 14.dp.toPx(), w - right)
        if (reveal > 0.95f) text(measurer, Js.fmt(vals.last()), lastX, ys(vals.last()) - 12.dp.toPx(), valueStyle, if (n == 1) Anchor.Center else Anchor.End)
        if (n == 1) text(measurer, points[0].label, xs(0), h - 8.dp.toPx(), labelStyle, Anchor.Center)
        else {
            text(measurer, points.first().label, left, h - 8.dp.toPx(), labelStyle, Anchor.Start)
            text(measurer, points.last().label, w - right, h - 8.dp.toPx(), labelStyle, Anchor.End)
        }
    }
}

/**
 * Столбчатая диаграмма со стопками. Горизонтальная линия цели (если задана) рисуется пунктиром.
 * Подписи по оси X берутся из столбцов, ось Y подписывается целыми значениями с красивым шагом.
 */
@Composable
fun StackedBarChart(
    columns: List<BarColumn>,
    description: String,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    minMax: Double = 1.0,
    height: Dp = 180.dp,
) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(color = muted, fontSize = 11.sp)
    val grow = revealProgress(columns)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = description; role = Role.Image },
    ) {
        val left = 36.dp.toPx(); val right = 10.dp.toPx(); val top = 16.dp.toPx(); val bottom = 28.dp.toPx()
        val w = size.width; val h = size.height
        var mx = max(minMax, columns.maxOfOrNull { it.total } ?: 0.0)
        if (goal != null) mx = max(mx, goal)
        val step = niceStepUp(mx / 3)
        mx = ceil(mx / step) * step
        fun ys(v: Double) = (top + (h - top - bottom) * (1 - v / mx)).toFloat()
        var t = 0.0
        while (t <= mx + 1e-9) {
            drawLine(grid, Offset(left, ys(t)), Offset(w - right, ys(t)), strokeWidth = 1.dp.toPx())
            text(measurer, Js.fmt(t), left - 6.dp.toPx(), ys(t), labelStyle, Anchor.End)
            t += step
        }
        val n = columns.size
        val slot = (w - left - right) / n
        val bw = slot * 0.6f
        columns.forEachIndexed { i, c ->
            val x = left + slot * i + (slot - bw) / 2
            var base = 0.0
            for (p in c.parts) {
                if (p.value <= 0) continue
                val y1 = ys(base + p.value); val y0 = ys(base)
                val hh = max(0f, y0 - y1 - 1.dp.toPx()) * grow
                drawRect(p.color, Offset(x, y0 - 1.dp.toPx() - hh), Size(bw, hh))
                base += p.value
            }
            text(measurer, c.label, x + bw / 2, h - 10.dp.toPx(), labelStyle, Anchor.Center)
        }
        if (goal != null) {
            drawLine(
                muted, Offset(left, ys(goal)), Offset(w - right, ys(goal)), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }
    }
}

/** Легенда диаграммы. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(it.color, CircleShape))
                androidx.compose.material3.Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Кольцевой индикатор прогресса с числом в центре. */
@Composable
fun RingIndicator(
    fraction: Float,
    centerText: String,
    subText: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 76.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val sweep by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val big = TextStyle(color = ink, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFamily = ru.diary.core.theme.UnboundedFamily)
    val small = TextStyle(color = muted, fontSize = 11.sp)
    Canvas(modifier.size(size).clearAndSetSemantics { contentDescription = "$centerText $subText"; role = Role.Image }) {
        val stroke = 7.dp.toPx()
        val d = this.size.minDimension - stroke
        val tl = Offset(stroke / 2, stroke / 2)
        drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
        drawArc(color, -90f, 360f * sweep, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        text(measurer, centerText, this.size.width / 2, this.size.height / 2 - 5.dp.toPx(), big, Anchor.Center)
        text(measurer, subText, this.size.width / 2, this.size.height / 2 + 12.dp.toPx(), small, Anchor.Center)
    }
}

/** Горизонтальная шкала доли выполнения. `mark` — положение вертикальной отметки цели (0..1). */
@Composable
fun MeterBar(
    fraction: Float,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    mark: Float? = null,
    height: Dp = 10.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "meter")
    val markColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(height + 6.dp).clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
        val h = height.toPx(); val y = (size.height - h) / 2
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        val fw = size.width * shown
        if (fw > 0) drawRoundRect(color, Offset(0f, y), Size(max(fw, h), h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        if (mark != null) drawLine(markColor, Offset(size.width * mark, y - 3.dp.toPx()), Offset(size.width * mark, y + h + 3.dp.toPx()), 2.dp.toPx())
    }
}

/** Мини-график без подписей для карточек. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, goal: Double? = null, color: Color = MaterialTheme.colorScheme.primary) {
    if (values.size < 2) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(34.dp).clearAndSetSemantics { }) {
        val all = if (goal != null) values + goal else values
        val mn = all.min(); val mx = all.max(); val sp = if (mx - mn == 0.0) 1.0 else mx - mn
        fun y(v: Double) = (size.height - 4.dp.toPx() - (v - mn) / sp * (size.height - 8.dp.toPx())).toFloat()
        if (goal != null) drawLine(muted, Offset(0f, y(goal)), Offset(size.width, y(goal)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = 4.dp.toPx() + i * (size.width - 8.dp.toPx()) / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Ряд точек «сделано / цель» (до 14). */
@Composable
fun DotRow(done: Int, total: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val off = MaterialTheme.colorScheme.outline
    Row(
        modifier.clearAndSetSemantics { contentDescription = "$done из $total" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(min(total, 14)) { i ->
            Box(Modifier.size(10.dp).background(if (i < done) color else off, CircleShape))
        }
    }
}

/** Пара столбцов за период: например «съедено» и «потрачено». */
data class GroupColumn(val label: String, val a: Double, val b: Double)

/** Сгруппированная столбчатая диаграмма из двух рядов с подписанными осями. */
@Composable
fun GroupedBarChart(
    columns: List<GroupColumn>,
    colorA: Color,
    colorB: Color,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outline
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    val grow = revealProgress(columns)
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
        val left = 44.dp.toPx(); val right = 10.dp.toPx(); val top = 16.dp.toPx(); val bottom = 28.dp.toPx()
        val w = size.width; val h = size.height
        var mx = max(1.0, columns.maxOfOrNull { max(it.a, it.b) } ?: 0.0)
        val step = niceStepUp(mx / 3)
        mx = ceil(mx / step) * step
        fun ys(v: Double) = (top + (h - top - bottom) * (1 - v / mx)).toFloat()
        var t = 0.0
        while (t <= mx + 1e-9) {
            drawLine(grid, Offset(left, ys(t)), Offset(w - right, ys(t)), strokeWidth = 1.dp.toPx())
            text(measurer, Js.fmt(t), left - 6.dp.toPx(), ys(t), labelStyle, Anchor.End)
            t += step
        }
        val slot = (w - left - right) / columns.size
        val bw = slot * 0.36f
        columns.forEachIndexed { i, c ->
            val x0 = left + slot * i + slot * 0.08f
            for ((k, pair) in listOf(c.a to colorA, c.b to colorB).withIndex()) {
                val hh = (ys(0.0) - ys(pair.first)) * grow
                if (hh > 0) drawRect(pair.second, Offset(x0 + k * (bw + 2.dp.toPx()), ys(0.0) - hh), Size(bw, hh))
            }
            text(measurer, c.label, left + slot * i + slot / 2, h - 10.dp.toPx(), labelStyle, Anchor.Center)
        }
    }
}
