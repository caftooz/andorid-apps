package ru.diary.core.ui

import androidx.compose.foundation.BorderStroke
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
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(bottom = 8.dp).semantics { heading() },
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier, textAlign = textAlign)
}

@Composable
fun DiaryCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

/** Блок-вердикт: подтверждение, предупреждение, пояснение. */
@Composable
fun Callout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
    ) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() } }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = MinTouch), enabled = enabled, shape = RoundedCornerShape(12.dp)) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.heightIn(min = MinTouch), enabled = enabled, shape = RoundedCornerShape(12.dp)) { Text(text) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick, modifier.heightIn(min = MinTouch), enabled = enabled, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DiaryTheme.extras.danger),
        border = BorderStroke(1.dp, DiaryTheme.extras.danger),
    ) { Text(text) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick, modifier.heightIn(min = MinTouch), enabled = enabled) { Text(text) }
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
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) },
                modifier = Modifier.heightIn(min = MinTouch),
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
