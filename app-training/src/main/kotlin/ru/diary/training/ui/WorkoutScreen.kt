package ru.diary.training.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import ru.diary.core.ui.BackLink
import ru.diary.core.ui.NumberBadge
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

    // Этапы тренировки: «warm» — разминка, «list» — список упражнений, «ex:<id>» — одно упражнение крупно.
    var stage by rememberSaveable {
        val started = active.ex.any { Logic.doneCount(it) > 0 }
        val warmDone = active.warmList.indices.all { active.warm[it] == true }
        mutableStateOf(if (active.warmList.isNotEmpty() && !warmDone && !started) "warm" else "list")
    }
    val focused = stage.removePrefix("ex:").takeIf { stage.startsWith("ex:") }?.let { id -> active.ex.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    LaunchedEffect(stage, focused) { if (stage.startsWith("ex:") && focused == null) stage = "list" }
    BackHandler(enabled = stage != "list") { stage = "list" }

    Column(Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = if (focused != null) "ex" else stage, modifier = Modifier.weight(1f), label = "stage",
            transitionSpec = {
                val fwd = targetState == "ex" || (initialState == "warm" && targetState == "list")
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { w -> if (fwd) w / 6 else -w / 6 }) togetherWith fadeOut(tween(160))
            },
        ) { st ->
            when {
                st == "warm" -> WarmStage(vm, active, onDone = { stage = "list" })
                st == "ex" && focused != null -> ExerciseStage(
                    vm, active, focused, onRestStarted = onRestStarted, onBack = { stage = "list" },
                    onNext = { id -> stage = if (id != null) "ex:$id" else "list" }, onFinish = { finishing = true },
                )
                else -> ListStage(vm, data, active, today, onWarm = { stage = "warm" }, onOpen = { id -> stage = "ex:$id" })
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

/** Этап 1: разминка отдельным экраном. */
@Composable
private fun WarmStage(vm: TrainingViewModel, a: ActiveWorkout, onDone: () -> Unit) {
    val done = a.warmList.indices.count { a.warm[it] == true }
    TabColumn {
        HeroHeader("Разминка", subtitle = "${a.title} · $done из ${a.warmList.size}", icon = "flame") {
            MeterBar(if (a.warmList.isNotEmpty()) done.toFloat() / a.warmList.size else 0f, "Разминка: $done из ${a.warmList.size}", Modifier.padding(top = 4.dp), color = DiaryTheme.extras.onHero)
        }
        DiaryCard {
            a.warmList.forEachIndexed { k, text -> CheckItem(text, a.warm[k] == true, { vm.toggleWarm(k) }) }
        }
        PrimaryButton(if (done == a.warmList.size) "Готово — к упражнениям" else "К упражнениям", onDone, Modifier.fillMaxWidth())
        if (done < a.warmList.size) QuietButton("Пропустить разминку", onDone)
    }
}

/** Этап 2: компактный список упражнений; нажатие открывает упражнение целиком. */
@Composable
private fun ListStage(vm: TrainingViewModel, data: TrainingData, active: ActiveWorkout, today: String, onWarm: () -> Unit, onOpen: (String) -> Unit) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var discardAsk by rememberSaveable { mutableStateOf(false) }
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
        val next = Logic.nextOpenEx(active)?.let { id -> active.ex.first { it.id == id } }
        if (next != null) PrimaryButton((if (doneSets == 0) "Начать: " else "Продолжить: ") + next.d.name, { onOpen(next.id) }, Modifier.fillMaxWidth())
        if (active.ex.isEmpty()) Callout { Text("Добавьте упражнения, которые будете делать. Можно выбрать из списка или создать своё.") }
        if (active.warmList.isNotEmpty()) {
            val wd = active.warmList.indices.count { active.warm[it] == true }
            SheetRow("Разминка", onWarm, subtitle = if (wd == active.warmList.size) "Готово" else "$wd из ${active.warmList.size}",
                leading = { IconBubble("flame", DiaryTheme.extras.volley) }, trailing = if (wd == active.warmList.size) "✓" else null)
        }
        active.ex.forEachIndexed { i, ex -> ExerciseRow(ex, i, isNext = ex.id == next?.id) { onOpen(ex.id) } }
        QuietButton("+ Добавить упражнение", { pickerOpen = true })
        if (pickerOpen) {
            ExercisePickerSheet(vm, data, active.ex.map { it.id }, onDismiss = { pickerOpen = false }) { d -> vm.addExerciseToWorkout(d); pickerOpen = false; onOpen(d.id) }
        }
        RestSettings(vm)
        if (discardAsk) {
            ConfirmBox("Удалить эту тренировку без сохранения?", "Да, удалить", { vm.discardWorkout() }, { discardAsk = false }, cancelLabel = "Нет")
        } else QuietButton("Отменить тренировку", { discardAsk = true })
    }
}

/** Краткое описание подходов: «3 × 8–10 · 40 кг» или «2 из 3 · 40 кг × 10». */
private fun exSummary(ex: ActiveEx): String {
    val done = Logic.doneCount(ex)
    val unit = if (ex.d.kind == "time") " с" else ""
    val reps = (if (ex.lo != ex.hi) "${ex.lo}–${ex.hi}" else "${ex.lo}") + unit
    val s = ex.sets.lastOrNull { it.done } ?: ex.sets.firstOrNull()
    val w = if (ex.d.kind == "w" && s != null && s.w > 0) " · ${Js.fmt(s.w)} кг" else ""
    return if (done == 0) "${ex.sets.size} × $reps$w" else "$done из ${ex.sets.size} подходов$w"
}

@Composable
private fun ExerciseRow(ex: ActiveEx, i: Int, isNext: Boolean, onClick: () -> Unit) {
    val done = Logic.doneCount(ex)
    val complete = ex.sets.isNotEmpty() && done == ex.sets.size
    SheetRow(
        ex.d.name, onClick, subtitle = exSummary(ex),
        leading = {
            if (complete) Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            } else NumberBadge(i + 1)
        },
        trailing = when { complete -> null; isNext -> "Дальше"; done > 0 -> "$done/${ex.sets.size}"; else -> null },
    )
}

/** Этап 3: одно упражнение крупно — текущий подход, обратная связь и список подходов. */
@Composable
private fun ExerciseStage(
    vm: TrainingViewModel, a: ActiveWorkout, i: Int, onRestStarted: () -> Unit, onBack: () -> Unit, onNext: (String?) -> Unit, onFinish: () -> Unit,
) {
    val ex = a.ex[i]
    val haptic = LocalHapticFeedback.current
    val weighted = ex.d.kind == "w"
    val cur = ex.sets.indexOfFirst { !it.done }.takeIf { it >= 0 }
    val lastDone = ex.sets.indexOfLast { it.done }.takeIf { it >= 0 }
    val per = ex.d.per.takeIf { it.isNotEmpty() }?.let { BuiltinData.perLabel[it] }
    TabColumn {
        BackLink("Все упражнения", onBack)
        Column {
            Text(ex.d.name, style = MaterialTheme.typography.headlineSmall)
            Muted(listOfNotNull("Упражнение ${i + 1} из ${a.ex.size}", exSummary(ex), per).joinToString(" · "))
        }
        if (ex.hint.isNotEmpty()) Callout { Text(ex.hint, style = MaterialTheme.typography.bodyMedium) }
        if (ex.first && weighted) Muted("Подбираем вес: после подхода отметьте, как далось, — следующий вес подстроится.")
        if (ex.d.tip.isNotEmpty()) Accordion("Техника") { Muted(ex.d.tip) }

        // Как далось последний выполненный подход — сразу после отметки, пока идёт отдых.
        if (weighted && lastDone != null && (cur == null || cur == lastDone + 1)) {
            DiaryCard {
                Text("Как далось: подход ${lastDone + 1}, ${Js.fmt(ex.sets[lastDone].w)} кг × ${ex.sets[lastDone].r}", style = MaterialTheme.typography.titleSmall)
                SegmentedControl(Texts.FB.map { it.key to it.value }, ex.sets[lastDone].fb, { vm.feedback(i, lastDone, it) })
            }
        }

        if (cur != null) {
            val s = ex.sets[cur]
            DiaryCard {
                Text("Подход ${cur + 1} из ${ex.sets.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (weighted) NumberStepper("Вес, кг", s.w, false, decimal = true, big = true, modifier = Modifier.fillMaxWidth(),
                    onStep = { vm.stepSet(i, cur, 'w', it) }, onType = { vm.inputSet(i, cur, 'w', it) })
                NumberStepper(if (ex.d.kind == "time") "Секунды" else "Повторы", s.r.toDouble(), false, decimal = false, big = true, modifier = Modifier.fillMaxWidth(),
                    onStep = { vm.stepSet(i, cur, 'r', it) }, onType = { vm.inputSet(i, cur, 'r', it) })
                PrimaryButton("✓  Подход выполнен", {
                    onRestStarted(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.toggleDone(i, cur)
                }, Modifier.fillMaxWidth().heightIn(min = 60.dp))
            }
        } else {
            val nextId = Logic.nextOpenEx(a)
            Callout { Text("Упражнение выполнено", style = MaterialTheme.typography.titleSmall) }
            if (nextId != null) {
                PrimaryButton("Следующее: " + a.ex.first { it.id == nextId }.d.name + " →", { onNext(nextId) }, Modifier.fillMaxWidth())
                SecondaryButton("Ко всем упражнениям", onBack, Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Закончить тренировку", onFinish, Modifier.fillMaxWidth())
                SecondaryButton("Ко всем упражнениям", onBack, Modifier.fillMaxWidth())
            }
        }

        DiaryCard {
            Text("Подходы", style = MaterialTheme.typography.titleSmall)
            ex.sets.forEachIndexed { j, s -> SetLine(ex, j, s, current = j == cur) { vm.toggleDone(i, j) } }
            QuietButton("+ Подход", { vm.addSet(i) })
        }
    }
}

/** Строка подхода в списке: номер, вес × повторы, отметка; у выполненного — «↶» для отмены. */
@Composable
private fun SetLine(ex: ActiveEx, j: Int, s: ActiveSet, current: Boolean, onUndo: () -> Unit) {
    val unit = if (ex.d.kind == "time") " с" else ""
    val value = (if (ex.d.kind == "w") "${Js.fmt(s.w)} кг × " else "") + "${s.r}$unit"
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when { s.done -> cs.primary.copy(alpha = 0.12f); current -> cs.surfaceVariant.copy(alpha = 0.7f); else -> androidx.compose.ui.graphics.Color.Transparent }, tween(300), label = "setLine",
    )
    Surface(shape = RoundedCornerShape(14.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp).heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (s.done) "✓" else "${j + 1}", style = MaterialTheme.typography.titleSmall, color = if (s.done || current) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(width = 20.dp, height = 24.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            s.fb?.let { Muted(Texts.FB[it] ?: "") }
            if (s.done) QuietButton("↶", onUndo, Modifier.semantics { contentDescription = "Отменить подход ${j + 1}" })
            else if (current) Muted("сейчас")
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

/** Значение с кнопками − и +. Набранный вручную текст не затирается, пока он совпадает со значением. */
@Composable
private fun NumberStepper(
    label: String, value: Double, locked: Boolean, decimal: Boolean, modifier: Modifier, onStep: (Int) -> Unit, onType: (String) -> Unit,
    big: Boolean = false,
) {
    fun show(v: Double) = if (v == 0.0 && decimal) "" else Js.fmt(v)
    var text by remember { mutableStateOf(show(value)) }
    LaunchedEffect(value) { if (Js.parseNum(text) != value) text = show(value) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Muted(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepCircle("−", "$label: меньше", !locked, big) { onStep(-1) }
            BasicTextField(
                value = text, onValueChange = { text = it; onType(it) }, enabled = !locked, singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
                    fontSize = (if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium).fontSize,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).heightIn(min = MinTouch).semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = MinTouch), contentAlignment = Alignment.Center) { inner() }
                },
            )
            StepCircle("+", "$label: больше", !locked, big) { onStep(1) }
        }
    }
}

/** Круглая кнопка «−» или «+» с нажатием-пружинкой. */
@Composable
private fun StepCircle(sign: String, description: String, enabled: Boolean, big: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick, Modifier.size(if (big) 64.dp else MinTouch).pressScale(src, 0.86f).semantics { contentDescription = description }, enabled = enabled,
        shape = CircleShape, color = cs.primary.copy(alpha = if (enabled) 0.14f else 0.05f), interactionSource = src,
        border = BorderStroke(1.dp, cs.primary.copy(alpha = if (enabled) 0.3f else 0.1f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(sign, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, color = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f))
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
                        Text("до следующего подхода", style = MaterialTheme.typography.bodySmall)
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
            // Во время отдыха панель занята таймером: «Закончить» возвращается, когда отдых пропущен или прошёл.
            if (resting || over) PrimaryButton(if (over) "Дальше" else "Пропустить", { vm.stopRest() })
            else PrimaryButton("Закончить", onFinish)
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
