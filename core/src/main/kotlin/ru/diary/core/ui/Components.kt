package ru.diary.core.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
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

/** Минимальный размер касания по рекомендациям доступности. */
val MinTouch = 48.dp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 18.dp, bottom = 2.dp).semantics { heading() },
    )
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier, textAlign = textAlign)
}

@Composable
fun DiaryCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) { Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp).animateContentSize(tween(240)), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

/** Плавное раскрытие блока: форма, тело карточки, пояснение. */
@Composable
fun Reveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible, modifier,
        enter = expandVertically(tween(240)) + fadeIn(tween(240)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
    ) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

/** Блок-вердикт: подтверждение, предупреждение, пояснение. */
@Composable
fun Callout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
    ) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    Button(
        onClick, modifier.heightIn(min = MinTouch).pressScale(src), enabled = enabled, shape = CircleShape, interactionSource = src,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick, modifier.heightIn(min = MinTouch).pressScale(src), enabled = enabled, shape = CircleShape, interactionSource = src,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick, modifier.heightIn(min = MinTouch).pressScale(src), enabled = enabled, shape = CircleShape, interactionSource = src,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DiaryTheme.extras.danger),
        border = BorderStroke(1.dp, DiaryTheme.extras.danger),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    TextButton(onClick, modifier.heightIn(min = MinTouch).pressScale(src), enabled = enabled, interactionSource = src, shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.labelLarge)
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
        options.forEach { (value, label) ->
            val src = remember { MutableInteractionSource() }
            FilterChip(
                selected = value == selected, onClick = { onSelect(value) }, label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                shape = CircleShape, interactionSource = src, modifier = Modifier.pressScale(src, 0.94f),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
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
) {
    OutlinedTextField(
        value = value, onValueChange = { if (it.length <= maxLength) onValueChange(it) }, label = { Text(label) },
        modifier = modifier.fillMaxWidth().heightIn(min = MinTouch), singleLine = singleLine, minLines = minLines,
        placeholder = placeholder?.let { { Text(it) } },
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
            OutlinedButton(
                onMinus, Modifier.size(MinTouch), enabled = minusEnabled, contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp),
            ) { Text("−", modifier = Modifier.semantics { contentDescription = "$label: меньше" }) }
            Text(
                value, modifier = Modifier.widthIn(min = 56.dp).padding(horizontal = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onPlus, Modifier.size(MinTouch), enabled = plusEnabled, contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp),
            ) { Text("+", modifier = Modifier.semantics { contentDescription = "$label: больше" }) }
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

/** Ссылка «‹ Назад» над вложенным экраном. */
@Composable
fun BackLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick, modifier.heightIn(min = MinTouch)) { Text("‹ $label") }
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
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(current, Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 2)
            Text("⌄")
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            options.forEach { (v, text) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(v); open = false }, modifier = Modifier.heightIn(min = MinTouch))
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
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().androidxHorizontalScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val src = remember { MutableInteractionSource() }
            FilterChip(
                selected = value == selected, onClick = { onSelect(value) }, label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                shape = CircleShape, interactionSource = src, modifier = Modifier.pressScale(src, 0.94f), border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun Modifier.androidxHorizontalScroll(): Modifier =
    this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))
