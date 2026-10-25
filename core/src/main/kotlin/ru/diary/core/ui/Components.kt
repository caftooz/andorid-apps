package ru.diary.core.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.diary.core.theme.DiaryTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import ru.diary.core.theme.mix

/** Минимальный размер касания по рекомендациям доступности. */
val MinTouch = 48.dp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 18.dp, bottom = 2.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 20.dp, height = 6.dp).background(DiaryTheme.extras.buttonBrush, CircleShape))
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Заголовок вложенного экрана: крупный шрифт с фирменным градиентом. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    val brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, DiaryTheme.extras.accent))
    Text(
        text, style = MaterialTheme.typography.headlineMedium.copy(brush = brush),
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier, textAlign = textAlign)
}

/** Стеклянная карточка: полупрозрачная подложка, светлый блик по краю и мягкая цветная тень. */
@Composable
fun DiaryCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val x = DiaryTheme.extras
    val primary = MaterialTheme.colorScheme.primary
    val border = BorderStroke(1.dp, Brush.linearGradient(listOf(x.rim, x.rim.copy(alpha = 0f), primary.copy(alpha = 0.22f))))
    val shadow = Modifier.shadow(if (x.dark) 0.dp else 10.dp, shape, ambientColor = primary.copy(alpha = 0.35f), spotColor = primary.copy(alpha = 0.22f))
    val body: @Composable () -> Unit = {
        // Без animateContentSize: высоту меняет только раскрывающийся блок внутри (Reveal). Две анимации размера
        // одновременно спорили друг с другом, и раскрытие то замедлялось, то снова ускорялось.
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
    if (onClick == null) {
        Surface(modifier.fillMaxWidth().then(shadow), shape = shape, color = x.glass, border = border) { body() }
    } else {
        // Вся карточка нажимается целиком: волна нажатия идёт по её границам, а не по маленькому внутреннему прямоугольнику.
        val src = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick, modifier = modifier.fillMaxWidth().pressScale(src, 0.985f).then(shadow), shape = shape, color = x.glass, border = border,
            interactionSource = src,
        ) { body() }
    }
}

/** Плавное раскрытие блока: форма, тело карточки, пояснение. */
@Composable
fun Reveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible, modifier,
        enter = expandVertically(tween(280, easing = LinearOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(220, delayMillis = 40)),
        exit = shrinkVertically(tween(220, easing = FastOutLinearInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(140)),
    ) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

/** Блок-вердикт: подтверждение, предупреждение, пояснение. Слева — фирменная градиентная полоска. */
@Composable
fun Callout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val x = DiaryTheme.extras
    val primary = MaterialTheme.colorScheme.primary
    val bar = Brush.verticalGradient(x.button.ifEmpty { listOf(primary, primary) })
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = mix(MaterialTheme.colorScheme.surface, primary, if (x.dark) 0.16f else 0.07f).copy(alpha = if (x.dark) 0.85f else 1f),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.18f)),
    ) {
        Column(
            Modifier.drawBehind {
                drawRoundRect(bar, Offset(6.dp.toPx(), 14.dp.toPx()), Size(4.dp.toPx(), size.height - 28.dp.toPx()), CornerRadius(2.dp.toPx()))
            }.padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/** Главная кнопка: градиентная «таблетка» с цветным свечением. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    val glow = x.button.firstOrNull() ?: cs.primary
    Surface(
        onClick, modifier.heightIn(min = MinTouch).pressScale(src)
            .shadow(if (enabled) 10.dp else 0.dp, CircleShape, ambientColor = glow, spotColor = glow),
        enabled = enabled, shape = CircleShape, color = Color.Transparent, interactionSource = src,
    ) {
        Box(
            Modifier.background(if (enabled) x.buttonBrush else SolidColor(cs.onSurface.copy(alpha = 0.10f)))
                .padding(horizontal = 26.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) x.onButton else cs.onSurface.copy(alpha = 0.38f), textAlign = TextAlign.Center)
        }
    }
}

/** Второстепенная кнопка: прозрачная заливка основным цветом и тонкая обводка. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    val tint = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f)
    Surface(
        onClick, modifier.heightIn(min = MinTouch).pressScale(src), enabled = enabled, shape = CircleShape, interactionSource = src,
        color = tint.copy(alpha = if (enabled) 0.13f else 0.06f), border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) mix(cs.primary, cs.onSurface, 0.15f) else tint, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val danger = DiaryTheme.extras.danger
    Surface(
        onClick, modifier.heightIn(min = MinTouch).pressScale(src), enabled = enabled, shape = CircleShape, interactionSource = src,
        color = danger.copy(alpha = 0.10f), border = BorderStroke(1.dp, danger.copy(alpha = 0.55f)),
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = danger, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    TextButton(
        onClick, modifier.heightIn(min = MinTouch).pressScale(src), enabled = enabled, interactionSource = src, shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Переключатель-«таблетка». Выбранный заливается фирменным градиентом (плавно) и получает галочку,
 * невыбранный остаётся стеклянным с тонкой обводкой.
 */
@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    val on by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "pill")
    val fg by animateColorAsState(if (selected) x.onButton else cs.onSurface, tween(220), label = "pillFg")
    val brush = x.buttonBrush
    Surface(
        selected = selected, onClick = onClick, shape = CircleShape, interactionSource = src,
        color = x.glass, border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.8f * (1f - on))),
        modifier = Modifier.pressScale(src, 0.94f),
    ) {
        Row(
            Modifier.drawBehind { drawRect(brush, alpha = on) }.heightIn(min = 40.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(selected, enter = fadeIn(tween(200)) + expandHorizontally(tween(220)), exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180))) {
                Icon(DiaryIcons.get("check"), null, tint = fg, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        }
    }
}

/** Ряд переключателей: ровно один выбранный вариант. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (value, label) -> Pill(label, value == selected) { onSelect(value) } }
    }
}

/** Переключатели с несколькими выбранными вариантами (например, «что болело»). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ToggleChips(
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (value, label) -> Pill(label, value in selected) { onToggle(value) } }
    }
}

/** Поле ввода с подписью. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    decimal: Boolean = false,
    singleLine: Boolean = true,
    placeholder: String? = null,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    password: Boolean = false,
) {
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value, onValueChange = { if (it.length <= maxLength) onValueChange(it) }, label = { Text(label) },
        modifier = modifier.fillMaxWidth().heightIn(min = MinTouch), singleLine = singleLine, minLines = minLines,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = x.glass, unfocusedContainerColor = x.glass,
            unfocusedBorderColor = cs.outline, focusedBorderColor = cs.primary,
        ),
        placeholder = placeholder?.let { { Text(it) } },
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else if (numeric) KeyboardType.Number else KeyboardType.Text),
    )
}

/** Кнопки «−» и «+» вокруг значения. Для TalkBack значение объявляется как «живая область». */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", "$label: меньше", onMinus, minusEnabled)
            Text(
                value, modifier = Modifier.widthIn(min = 56.dp).padding(horizontal = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
            )
            StepButton("+", "$label: больше", onPlus, plusEnabled)
        }
    }
}

/** Круглая кнопка шага для [Stepper]. */
@Composable
private fun StepButton(sign: String, description: String, onClick: () -> Unit, enabled: Boolean) {
    val src = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick, Modifier.size(MinTouch).pressScale(src, 0.88f).semantics { contentDescription = description }, enabled = enabled,
        shape = CircleShape, color = cs.primary.copy(alpha = if (enabled) 0.14f else 0.05f), interactionSource = src,
        border = BorderStroke(1.dp, cs.primary.copy(alpha = if (enabled) 0.3f else 0.1f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(sign, style = MaterialTheme.typography.titleLarge, color = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f))
        }
    }
}

/** Запрос подтверждения перед необратимым действием. */
@Composable
fun ConfirmBox(
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    cancelLabel: String = "Отмена",
) {
    Callout {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DangerButton(confirmLabel, onConfirm)
            SecondaryButton(cancelLabel, onCancel)
        }
    }
}

/**
 * Поле, которое сохраняет значение не на каждую букву, а после короткой паузы или при уходе с экрана.
 * Так правка текста в редакторах не заставляет перечитывать всю базу на каждом нажатии.
 */
@Composable
fun DebouncedField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    decimal: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    placeholder: String? = null,
    delayMs: Long = 600,
) {
    var text by remember { mutableStateOf(value) }
    // Последнее значение, которое поле уже отправило наружу: пока оно совпадает с value, поле не перезаписывается.
    var committed by remember { mutableStateOf(value) }
    val latest = rememberUpdatedState(onCommit)
    val state = rememberUpdatedState(text to committed)
    LaunchedEffect(value) {
        if (value != committed) { text = value; committed = value }
    }
    LaunchedEffect(text) {
        if (text != committed) {
            kotlinx.coroutines.delay(delayMs)
            committed = text
            latest.value(text)
        }
    }
    DisposableEffect(Unit) {
        onDispose { val (t, c) = state.value; if (t != c) latest.value(t) }
    }
    LabeledField(label, text, { text = it }, modifier, numeric, decimal, singleLine, placeholder, minLines, maxLength)
}

/** Ссылка «‹ Назад» над вложенным экраном: стеклянная «таблетка» со стрелкой. */
@Composable
fun BackLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick, modifier.heightIn(min = MinTouch).pressScale(src), shape = CircleShape, color = DiaryTheme.extras.glass,
        border = BorderStroke(1.dp, cs.outline), interactionSource = src,
    ) {
        Row(Modifier.padding(start = 10.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(cs.primary.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Text("‹", style = MaterialTheme.typography.titleMedium, color = cs.primary)
            }
            Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
        }
    }
}

/** Выпадающий список для выбора одного значения (упражнение, показатель). */
@Composable
fun <T> DropdownSelect(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: ""
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth()) {
        OutlinedButton(
            { open = true }, Modifier.fillMaxWidth().heightIn(min = MinTouch).semantics { contentDescription = "$label: $current" },
            shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = DiaryTheme.extras.glass, contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(current, Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 2)
            Text("⌄", color = MaterialTheme.colorScheme.primary)
        }
        androidx.compose.material3.DropdownMenu(
            open, { open = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(22.dp),
        ) {
            options.forEach { (v, text) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text, color = MaterialTheme.colorScheme.onSurface) }, onClick = { onSelect(v); open = false },
                    modifier = Modifier.heightIn(min = MinTouch).padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Те же переключатели, но в одну строку с прокруткой: для длинных списков категорий и программ. */
@Composable
fun <T> ChoiceChipsRow(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().androidxHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Pill(label, value == selected) { onSelect(value) } }
    }
}

@Composable
private fun Modifier.androidxHorizontalScroll(): Modifier =
    this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))

/** Диалог с контрастным фоном: текст и заголовок всегда читаются, в том числе в динамической тёмной теме. */
@Composable
fun DiaryAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton,
        title = title, text = text, shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
    )
}
