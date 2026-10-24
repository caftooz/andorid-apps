package ru.diary.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.diary.core.theme.DiaryTheme
import kotlin.math.abs

/**
 * Нажатие с волной и «пружинящим» масштабом для любых строк и плашек, у которых нет своей кнопки.
 */
fun Modifier.pressClickable(role: Role = Role.Button, scale: Float = 0.97f, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    this.pressScale(src, scale).clickable(src, ripple(), enabled = enabled, role = role, onClick = onClick)
}

/**
 * Сегментный переключатель: несколько коротких вариантов в одной дорожке, выбранный подсвечивает
 * градиентная капсула, которая плавно переезжает между сегментами. Подходит для 2–5 коротких подписей.
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    val n = options.size.coerceAtLeast(1)
    val idx = options.indexOfFirst { it.first == selected }
    val pos by animateFloatAsState(idx.coerceAtLeast(0).toFloat(), spring(dampingRatio = 0.82f, stiffness = 520f), label = "segPos")
    val shown by animateFloatAsState(if (idx >= 0) 1f else 0f, tween(200), label = "segShown")
    val brush = x.buttonBrush
    val glow = x.button.firstOrNull() ?: cs.primary
    val style = if (n >= 4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    Box(
        modifier.fillMaxWidth().height(52.dp)
            .background(cs.onSurface.copy(alpha = if (x.dark) 0.07f else 0.045f), CircleShape)
            .border(1.dp, cs.outline.copy(alpha = 0.7f), CircleShape)
            .padding(4.dp),
    ) {
        // Капсула ездит под подписями: сдвиг считается от её собственной ширины, равной ширине сегмента.
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(1f / n)
                .graphicsLayer { translationX = pos * size.width; alpha = shown }
                .shadow(8.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .background(brush, CircleShape),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            options.forEachIndexed { i, (value, label) ->
                val src = remember { MutableInteractionSource() }
                // Цвет подписи зависит от того, насколько капсула близко: при переезде текст перекрашивается плавно.
                val near = (1f - abs(pos - i)).coerceIn(0f, 1f) * shown
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .selectable(value == selected, src, ripple(), role = Role.RadioButton) { onSelect(value) }
                        .pressScale(src, 0.94f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = style, color = lerp(cs.onSurfaceVariant, x.onButton, near),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/** Вариант для [LetterPicker]: крупная буква на плашке и полное название для подписи и TalkBack. */
data class LetterOption<T>(val value: T, val letter: String, val label: String)

/**
 * Выбор крупными квадратными плашками с буквой: короткий и наглядный выбор программы, когда полное название
 * всё равно показано рядом. Выбранная плашка заливается градиентом и слегка увеличивается.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> LetterPicker(
    options: List<LetterOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tile: Dp = 64.dp,
) {
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { o ->
            val sel = o.value == selected
            val src = remember { MutableInteractionSource() }
            val on by animateFloatAsState(if (sel) 1f else 0f, tween(220), label = "letter")
            val grow by animateFloatAsState(if (sel) 1.06f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "letterGrow")
            val fg by animateColorAsState(if (sel) x.onButton else cs.onSurface, tween(220), label = "letterFg")
            val glow = x.button.firstOrNull() ?: cs.primary
            val brush = x.buttonBrush
            val shape = RoundedCornerShape(tile * 0.32f)
            Surface(
                selected = sel, onClick = { onSelect(o.value) }, shape = shape, color = x.glass, interactionSource = src,
                border = BorderStroke(1.dp, cs.outline.copy(alpha = 1f - on)),
                modifier = Modifier.size(tile).graphicsLayer { scaleX = grow; scaleY = grow }.pressScale(src, 0.9f)
                    .shadow(12.dp * on, shape, ambientColor = glow, spotColor = glow)
                    .semantics { contentDescription = o.label },
            ) {
                Box(Modifier.drawBehind { drawRect(brush, alpha = on) }, contentAlignment = Alignment.Center) {
                    Text(o.letter, style = MaterialTheme.typography.headlineMedium, color = fg, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Шкала 1…N: кружки с цифрами на одной линии. Линия и кружки до выбранного заливаются градиентом,
 * под шкалой — подпись выбранного уровня. Для нагрузки и самочувствия.
 */
@Composable
fun ScalePicker(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    val n = labels.size
    val fill by animateFloatAsState(((selected - 1).toFloat() / (n - 1).coerceAtLeast(1)).coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = 420f), label = "scale")
    val track = cs.outline
    val brush = x.buttonBrush
    val dot = 44.dp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(dot)) {
            Canvas(Modifier.fillMaxWidth().height(dot).clearAndSetSemantics { }) {
                val r = dot.toPx() / 2; val y = size.height / 2; val h = 4.dp.toPx()
                drawRoundRect(track, Offset(r, y - h / 2), Size(size.width - 2 * r, h), CornerRadius(h / 2))
                drawRoundRect(brush, Offset(r, y - h / 2), Size((size.width - 2 * r) * fill, h), CornerRadius(h / 2))
            }
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 1..n) {
                    val reached = i <= selected
                    val on by animateFloatAsState(if (reached) 1f else 0f, tween(200, delayMillis = if (reached) (i - 1) * 30 else 0), label = "scaleDot")
                    val fg by animateColorAsState(if (reached) x.onButton else cs.onSurfaceVariant, tween(200), label = "scaleFg")
                    val src = remember { MutableInteractionSource() }
                    Surface(
                        selected = i == selected, onClick = { onSelect(i) }, shape = CircleShape, color = x.glass.copy(alpha = 1f), interactionSource = src,
                        border = BorderStroke(if (i == selected) 2.dp else 1.dp, if (i == selected) cs.primary else track.copy(alpha = 1f - on)),
                        modifier = Modifier.size(dot).pressScale(src, 0.88f).semantics { contentDescription = "$i, ${labels[i - 1]}" },
                    ) {
                        Box(Modifier.drawBehind { drawRect(brush, alpha = on) }, contentAlignment = Alignment.Center) {
                            Text("$i", style = MaterialTheme.typography.titleMedium, color = fg)
                        }
                    }
                }
            }
        }
        Text(
            if (selected in 1..n) labels[selected - 1] else "", style = MaterialTheme.typography.labelLarge, color = cs.primary,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

/** Вариант для [IconTiles]: значок, подпись и необязательный свой цвет. */
data class TileOption<T>(val value: T, val icon: String, val label: String, val color: Color? = null)

/**
 * Выбор плитками со значками: для категорий и типов, которые легче узнать по картинке.
 * Помещается до 4–5 плиток в ряд.
 */
@Composable
fun <T> IconTiles(
    options: List<TileOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val sel = o.value == selected
            val color = o.color ?: cs.primary
            val src = remember { MutableInteractionSource() }
            val bg by animateColorAsState(if (sel) color.copy(alpha = 0.16f) else x.glass, tween(220), label = "tileBg")
            val line by animateColorAsState(if (sel) color else cs.outline, tween(220), label = "tileLine")
            Surface(
                selected = sel, onClick = { onSelect(o.value) }, shape = RoundedCornerShape(20.dp), color = bg, interactionSource = src,
                border = BorderStroke(if (sel) 2.dp else 1.dp, line),
                modifier = Modifier.weight(1f).heightIn(min = 80.dp).pressScale(src, 0.93f).semantics { contentDescription = o.label },
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    if (sel) IconBadge(o.icon, color, size = 34.dp) else IconBubble(o.icon, color, size = 34.dp)
                    Text(o.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Переключатель «вкл/выкл»: дорожка заливается градиентом, бегунок переезжает с лёгким отскоком и получает галочку. */
@Composable
fun DiarySwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    val on by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 520f), label = "switch")
    val brush = x.buttonBrush
    val off = cs.onSurface.copy(alpha = 0.16f)
    val mark = x.button.firstOrNull() ?: cs.primary
    Canvas(modifier.size(width = 52.dp, height = 32.dp).clearAndSetSemantics { }) {
        val h = size.height; val r = h / 2
        drawRoundRect(off, size = size, cornerRadius = CornerRadius(r))
        drawRoundRect(brush, size = size, cornerRadius = CornerRadius(r), alpha = on.coerceIn(0f, 1f))
        val tr = r - 4.dp.toPx()
        val cx = r + (size.width - 2 * r) * on
        drawCircle(Color.Black.copy(alpha = 0.12f), tr + 1.dp.toPx(), Offset(cx, r + 1.dp.toPx()))
        drawCircle(Color.White, tr, Offset(cx, r))
        if (on > 0.5f) {
            val a = ((on - 0.5f) * 2).coerceIn(0f, 1f)
            val p = Path().apply {
                moveTo(cx - tr * 0.42f, r); lineTo(cx - tr * 0.1f, r + tr * 0.32f); lineTo(cx + tr * 0.45f, r - tr * 0.3f)
            }
            drawPath(p, mark.copy(alpha = a), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Строка настройки с переключателем: нажимается целиком, с волной и «пружинкой». */
@Composable
fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
            .toggleable(checked, src, ripple(), role = Role.Switch, onValueChange = onChange)
            .pressScale(src, 0.98f).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Muted(subtitle)
        }
        DiarySwitch(checked)
    }
}

/** Пункт списка с круглой отметкой: галочка рисуется штрихом, кружок заливается градиентом. */
@Composable
fun CheckItem(text: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(260), label = "check")
    val pop by animateFloatAsState(if (checked) 1f else 0.9f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "checkPop")
    val brush = x.buttonBrush
    val line = cs.outline
    val tick = x.onButton
    Row(
        modifier.fillMaxWidth().heightIn(min = MinTouch).clip(RoundedCornerShape(16.dp))
            .toggleable(checked, src, ripple(), role = Role.Checkbox) { onToggle() }
            .pressScale(src, 0.98f).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(28.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clearAndSetSemantics { }) {
            val r = size.minDimension / 2
            drawCircle(line, r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()), alpha = 1f - on)
            drawCircle(brush, r, alpha = on)
            if (on > 0f) {
                val p = Path().apply { moveTo(r * 0.55f, r * 1.02f); lineTo(r * 0.88f, r * 1.35f); lineTo(r * 1.45f, r * 0.7f) }
                val m = PathMeasure().apply { setPath(p, false) }
                val part = Path(); m.getSegment(0f, m.length * on, part, true)
                drawPath(part, tick, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text(
            text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            color = if (checked) cs.onSurfaceVariant else cs.onSurface,
        )
    }
}
