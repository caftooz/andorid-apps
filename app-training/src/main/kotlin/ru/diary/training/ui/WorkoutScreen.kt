package ru.diary.training.ui

import ru.diary.core.ui.IconBubble
import ru.diary.core.ui.SheetRow
import ru.diary.core.ui.DiarySheet
import androidx.compose.foundation.interaction.MutableInteractionSource
import ru.diary.core.ui.pressScale
import ru.diary.core.ui.ToggleChips
import ru.diary.core.ui.CheckItem
import ru.diary.core.ui.ScalePicker
import ru.diary.core.ui.SegmentedControl
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.charts.MeterBar
import ru.diary.core.ui.HeroHeader
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ru.diary.core.ui.DiaryAlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import ru.diary.core.ui.Reveal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.diary.core.ui.Callout
import ru.diary.core.ui.ChoiceChips
import ru.diary.core.ui.ConfirmBox
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.MinTouch
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.util.Js
import ru.diary.training.domain.ActiveEx
import ru.diary.training.domain.ActiveSet
import ru.diary.training.domain.ActiveWorkout
import ru.diary.training.domain.BuiltinData
import ru.diary.training.domain.Logic
import ru.diary.training.domain.TrainingData

/**
 * Экран идущей тренировки: разминка, упражнения с подходами, обратная связь, таймер отдыха и завершение.
 * Состояние хранится во ViewModel и в Room после каждого действия.
 */
@Composable
fun WorkoutScreen(vm: TrainingViewModel, data: TrainingData, active: ActiveWorkout, today: String) {
    var finishing by rememberSaveable { mutableStateOf(false) }
    var openEx by rememberSaveable { mutableStateOf(active.ex.firstOrNull()?.id) }
    var warmOpen by rememberSaveable { mutableStateOf(true) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var discardAsk by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var askNotif by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // Разрешение на уведомления просим в момент, когда оно впервые нужно: после первого выполненного подхода.
    val onRestStarted = {
        if (Build.VERSION.SDK_INT >= 33 && !settings.notificationsAsked &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotif = true
    }
    if (askNotif) {
        DiaryAlertDialog(
            onDismissRequest = { askNotif = false; vm.markNotificationsAsked() },
            title = { Text("Сигнал об окончании отдыха") },
            text = { Text("Разрешите уведомления, чтобы телефон сообщил об окончании отдыха, даже когда экран выключен.") },
            confirmButton = {
                QuietButton("Разрешить", {
                    askNotif = false; vm.markNotificationsAsked()
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            },
            dismissButton = { QuietButton("Не сейчас", { askNotif = false; vm.markNotificationsAsked() }) },
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) {
            run {
                TabColumn {
                    if (Logic.deloadNow(data.plan, today)) Callout { Text("Неделя разгрузки: подходов вдвое меньше, веса те же.") }
                    Logic.volleyNote(data.plan, data.sessions, today).takeIf { it.isNotEmpty() }?.let { Callout { Text(it) } }
                    val doneSets = active.ex.sumOf { Logic.doneCount(it) }
                    val allSets = active.ex.sumOf { it.sets.size }
                    HeroHeader(active.title, subtitle = "Идёт тренировка · $doneSets из $allSets подходов", icon = "gym") {
                        MeterBar(
                            if (allSets > 0) doneSets.toFloat() / allSets else 0f, "Выполнено $doneSets из $allSets подходов",
                            Modifier.padding(top = 4.dp), color = DiaryTheme.extras.onHero,
                        )
                    }
                    RestSettings(vm)
                    if (active.ex.any { it.first && it.d.kind == "w" }) {
                        Callout { Text("Подбираем веса. После каждого подхода нажмите «Легко», «Нормально» или «Тяжело». Приложение подскажет следующий вес и запомнит рабочий.") }
                    }
                    if (active.ex.isEmpty()) Callout { Text("Добавьте упражнения, которые будете делать. Можно выбрать из списка или создать своё.") }
                    if (active.warmList.isNotEmpty()) WarmCard(vm, active, warmOpen) { warmOpen = !warmOpen }
                    active.ex.forEachIndexed { i, ex ->
                        ExerciseCard(vm, ex, i, open = openEx == ex.id, onToggle = { openEx = if (openEx == ex.id) null else ex.id }, onRestStarted = onRestStarted) { next -> openEx = next }
                    }
                    QuietButton("+ Добавить упражнение", { pickerOpen = true })
                    if (pickerOpen) {
                        ExercisePickerSheet(vm, data, active.ex.map { it.id }, onDismiss = { pickerOpen = false }) { d -> vm.addExerciseToWorkout(d); openEx = d.id; pickerOpen = false }
                    }
                    if (discardAsk) {
                        ConfirmBox("Удалить эту тренировку без сохранения?", "Да, удалить", { vm.discardWorkout() }, { discardAsk = false }, cancelLabel = "Нет")
                    } else QuietButton("Отменить тренировку", { discardAsk = true })
                }
            }
        }
        WorkoutBar(vm, active) { finishing = true }
    }
    // Завершение тренировки — во всплывающей панели поверх упражнений: можно закрыть её и вернуться.
    if (finishing) {
        DiarySheet({ finishing = false }, title = "Тренировка закончена", subtitle = active.title, icon = "check") { close ->
            FinishForm(vm, active, onBack = close)
        }
    }
}

/** Строка «Отдых между подходами» с текущим значением; настройка открывается во всплывающей панели. */
@Composable
private fun RestSettings(vm: TrainingViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    SheetRow(
        "Отдых между подходами", { open = true }, subtitle = if (s.restAuto) "Время берётся из упражнения" else "Одинаковый для всех упражнений",
        leading = { IconBubble("rest", MaterialTheme.colorScheme.primary) }, trailing = if (s.restAuto) "Авто" else "${s.restLen} с",
    )
    if (open) DiarySheet({ open = false }, title = "Отдых между подходами", icon = "rest") { close ->
        RestSettingsBody(vm)
        PrimaryButton("Готово", close, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RestSettingsBody(vm: TrainingViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var custom by rememberSaveable { mutableStateOf(s.restLen !in listOf(60, 90, 120)) }
    var text by rememberSaveable { mutableStateOf(s.restLen.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = listOf("auto" to "Авто", "60" to "60 с", "90" to "90 с", "120" to "120 с", "own" to "Своё")
        val sel = when {
            s.restAuto -> "auto"
            custom -> "own"
            else -> s.restLen.toString()
        }
        SegmentedControl(options, sel, {
            when (it) {
                "auto" -> vm.setRestAuto(true)
                "own" -> { custom = true; vm.setRestAuto(false) }
                else -> { custom = false; vm.setRestAuto(false); vm.setRestLen(it.toInt()) }
            }
        })
        if (!s.restAuto && custom) {
            LabeledField("Отдых, секунд", text, { t ->
                text = t
                t.toIntOrNull()?.takeIf { it in 5..3600 }?.let(vm::setRestLen)
            }, numeric = true)
        }
    }
}

@Composable
private fun WarmCard(vm: TrainingViewModel, a: ActiveWorkout, open: Boolean, onToggle: () -> Unit) {
    val done = a.warmList.indices.count { a.warm[it] == true }
    DiaryCard(onClick = if (!open) onToggle else null) {
        CardHeader("Разминка", "$done/${a.warmList.size}", open, onToggle)
        Reveal(open) {
            a.warmList.forEachIndexed { k, text ->
                CheckItem(text, a.warm[k] == true, { vm.toggleWarm(k) })
            }
        }
    }
}

@Composable
private fun CardHeader(title: String, count: String, open: Boolean, onToggle: () -> Unit, subtitle: String? = null) {
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onToggle, color = androidx.compose.ui.graphics.Color.Transparent, interactionSource = src, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().pressScale(src, 0.98f).heightIn(min = MinTouch).semantics { stateDescription = if (open) "развёрнуто" else "свёрнуто"; contentDescription = "$title, $count" },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Muted(it) }
            }
            Text(count, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExerciseCard(vm: TrainingViewModel, ex: ActiveEx, i: Int, open: Boolean, onToggle: () -> Unit, onRestStarted: () -> Unit, onOpenNext: (String?) -> Unit) {
    val done = Logic.doneCount(ex)
    val per = ex.d.per.takeIf { it.isNotEmpty() }?.let { BuiltinData.perLabel[it] }
    DiaryCard(onClick = if (!open) onToggle else null) {
        CardHeader(ex.d.name, "$done/${ex.sets.size}", open, onToggle, subtitle = per)
        Reveal(open) {
            if (ex.d.tip.isNotEmpty()) Muted(ex.d.tip)
            if (ex.hint.isNotEmpty()) Text(ex.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            ex.sets.forEachIndexed { j, s -> SetRow(vm, ex, i, j, s, onRestStarted, onOpenNext) }
            SecondaryButton("+ Подход", { vm.addSet(i) })
        }
    }
}

@Composable
private fun SetRow(vm: TrainingViewModel, ex: ActiveEx, i: Int, j: Int, s: ActiveSet, onRestStarted: () -> Unit, onOpenNext: (String?) -> Unit) {
    val weighted = ex.d.kind == "w"
    val repsLabel = if (ex.d.kind == "time") "Секунды" else "Повторы"
    val rowColor by animateColorAsState(
        if (s.done) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), tween(300), label = "set",
    )
    val haptic = LocalHapticFeedback.current
    val checkScale by animateFloatAsState(if (s.done) 1.08f else 1f, spring(dampingRatio = 0.45f, stiffness = 400f), label = "check")
    Surface(
        shape = RoundedCornerShape(18.dp), color = rowColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Подход ${j + 1}", style = MaterialTheme.typography.titleSmall)
                val checkSrc = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = {
                        if (!s.done) { onRestStarted(); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        vm.toggleDone(i, j)?.let(onOpenNext)
                    },
                    interactionSource = checkSrc,
                    modifier = Modifier.size(MinTouch).scale(checkScale).pressScale(checkSrc, 0.85f).semantics {
                        role = Role.Checkbox
                        contentDescription = (if (s.done) "Отменить подход " else "Подход выполнен: ") + (j + 1)
                        selected = s.done
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), shape = CircleShape,
                    border = if (s.done) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    colors = if (s.done) androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                ) { Text("✓") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (weighted) NumberStepper("Вес, кг", s.w, s.done, decimal = true, modifier = Modifier.weight(1f),
                    onStep = { vm.stepSet(i, j, 'w', it) }, onType = { vm.inputSet(i, j, 'w', it) })
                NumberStepper(repsLabel, s.r.toDouble(), s.done, decimal = false, modifier = Modifier.weight(1f),
                    onStep = { vm.stepSet(i, j, 'r', it) }, onType = { vm.inputSet(i, j, 'r', it) })
            }
            Reveal(s.done && weighted) {
                Muted("Как далось:")
                SegmentedControl(Texts.FB.map { it.key to it.value }, s.fb, { vm.feedback(i, j, it) })
            }
        }
    }
}

/** Значение с кнопками − и +. Набранный вручную текст не затирается, пока он совпадает со значением. */
@Composable
private fun NumberStepper(
    label: String, value: Double, locked: Boolean, decimal: Boolean, modifier: Modifier, onStep: (Int) -> Unit, onType: (String) -> Unit,
) {
    fun show(v: Double) = if (v == 0.0 && decimal) "" else Js.fmt(v)
    var text by remember { mutableStateOf(show(value)) }
    LaunchedEffect(value) { if (Js.parseNum(text) != value) text = show(value) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Muted(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepCircle("−", "$label: меньше", !locked) { onStep(-1) }
            BasicTextField(
                value = text, onValueChange = { text = it; onType(it) }, enabled = !locked, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.titleMedium.fontSize),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).heightIn(min = MinTouch).semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = MinTouch), contentAlignment = Alignment.Center) { inner() }
                },
            )
            StepCircle("+", "$label: больше", !locked) { onStep(1) }
        }
    }
}

/** Круглая кнопка «−» или «+» с нажатием-пружинкой. */
@Composable
private fun StepCircle(sign: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick, Modifier.size(MinTouch).pressScale(src, 0.86f).semantics { contentDescription = description }, enabled = enabled,
        shape = CircleShape, color = cs.primary.copy(alpha = if (enabled) 0.14f else 0.05f), interactionSource = src,
        border = BorderStroke(1.dp, cs.primary.copy(alpha = if (enabled) 0.3f else 0.1f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(sign, style = MaterialTheme.typography.titleLarge, color = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f))
        }
    }
}

/** Нижняя панель: отсчёт отдыха или время тренировки и кнопка «Закончить». */
@Composable
private fun WorkoutBar(vm: TrainingViewModel, a: ActiveWorkout, onFinish: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val left = if (a.restEnd > 0) ((a.restEnd - now + 999) / 1000).toInt() else 0
    val resting = a.restEnd > 0 && left > 0
    val over = a.restEnd > 0 && left <= 0
    LaunchedEffect(over, a.restEnd) { if (over && now - a.restEnd > 90_000) vm.stopRest() }
    val x = DiaryTheme.extras
    val on by animateFloatAsState(if (over) 1f else 0f, tween(350), label = "barOn")
    val fg by animateColorAsState(if (over) x.onHero else MaterialTheme.colorScheme.onSurface, tween(350), label = "barFg")
    val glow = x.hero.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val hero = x.heroBrush
    val shape = RoundedCornerShape(28.dp)
    Surface(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).shadow(14.dp, shape, ambientColor = glow, spotColor = glow),
        shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = fg,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(x.rim, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))),
    ) {
        Row(
            Modifier.fillMaxWidth().drawBehind { drawRect(hero, alpha = on) }.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { stateDescription = if (resting) "Отдых" else "Тренировка" }) {
                when {
                    resting -> {
                        Text("Отдых ${left / 60}:${"%02d".format(left % 60)}", style = MaterialTheme.typography.titleLarge)
                        Text("Следующий подход, когда будете готовы", style = MaterialTheme.typography.bodySmall)
                    }
                    over -> {
                        Text("Отдых закончен", style = MaterialTheme.typography.titleLarge)
                        Text("Следующий подход", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        val min = maxOf(1.0, Js.round((now - a.start) / 60000.0)).toInt()
                        Text("$min мин", style = MaterialTheme.typography.titleLarge)
                        Text("Идёт тренировка", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (resting || over) QuietButton("Пропустить", { vm.stopRest() })
            PrimaryButton("Закончить", onFinish)
        }
    }
}

@Composable
private fun FinishForm(vm: TrainingViewModel, a: ActiveWorkout, onBack: () -> Unit) {
    var rpe by rememberSaveable { mutableIntStateOf(3) }
    var pain by rememberSaveable { mutableStateOf(listOf<String>()) }
    var kcal by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var discardAsk by rememberSaveable { mutableStateOf(false) }
    val sets = a.ex.sumOf { Logic.doneCount(it) }
    val exs = a.ex.count { Logic.doneCount(it) > 0 }
    val min = maxOf(1.0, Js.round((System.currentTimeMillis() - a.start) / 60000.0)).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Callout {
            Text(
                "$sets ${Js.plural(sets, "подход", "подхода", "подходов")} в $exs ${Js.plural(exs, "упражнении", "упражнениях", "упражнениях")} · $min мин",
                style = MaterialTheme.typography.titleSmall,
            )
            if (sets == 0) Muted("Ни один подход не отмечен. Сохранить такую запись не получится, но можно вернуться или удалить тренировку.")
        }
        Muted("Как далась тренировка")
        ScalePicker(Texts.RPE_LABEL.drop(1), rpe, { rpe = it })
        Muted("Что-то болело или щёлкало")
        ToggleChips(Texts.PAIN.map { it to it }, pain.toSet(), { p -> pain = if (p in pain) pain - p else pain + p })
        LabeledField("Калории, ккал, по желанию", kcal, { kcal = it }, numeric = true)
        LabeledField("Заметка, по желанию", note, { note = it }, maxLength = 1000, singleLine = false, minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Сохранить тренировку", { vm.finishWorkout(Logic.Finish(rpe, pain, note, kcal)) }, enabled = sets > 0)
            SecondaryButton("Вернуться", onBack)
        }
        if (discardAsk) ConfirmBox("Удалить без сохранения?", "Да, удалить", { vm.discardWorkout() }, { discardAsk = false }, cancelLabel = "Нет")
        else QuietButton("Не сохранять", { discardAsk = true })
    }
}
