package ru.diary.training.ui

import ru.diary.core.ui.NumberBadge
import ru.diary.core.ui.SheetRow
import ru.diary.core.ui.DiarySheet
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import ru.diary.core.ui.pressScale
import ru.diary.core.ui.IconBubble
import ru.diary.core.ui.ToggleRow
import ru.diary.core.ui.SegmentedControl
import ru.diary.core.ui.LetterOption
import ru.diary.core.ui.LetterPicker
import ru.diary.core.ui.TileOption
import ru.diary.core.ui.IconTiles
import ru.diary.core.ui.HeroHeader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.diary.core.charts.MeterBar
import ru.diary.core.charts.RingIndicator
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.ui.BackLink
import ru.diary.core.ui.ChoiceChips
import ru.diary.core.ui.ConfirmBox
import ru.diary.core.ui.DangerButton
import ru.diary.core.ui.DebouncedField
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.DiaryIcons
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.MinTouch
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.Reveal
import ru.diary.core.ui.ScreenTitle
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.ui.SectionTitle
import ru.diary.core.ui.Stepper
import ru.diary.core.util.Js
import ru.diary.training.domain.BuiltinData
import ru.diary.training.domain.ExerciseCatalog
import ru.diary.training.domain.Logic
import ru.diary.training.domain.PlanEdit
import ru.diary.training.domain.Program
import ru.diary.training.domain.Transfer
import ru.diary.training.domain.TrainingData
import ru.diary.training.domain.WEEKLY_GOAL

fun NavGraphBuilder.planGraph(nav: NavHostController, vm: TrainingViewModel, data: TrainingData, today: String) {
    composable("plan/days") { DaysEditor(vm, data, nav::popBackStack) }
    composable("plan/progs") { ProgramsScreen(vm, data, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("plan/progs/$it") }) }
    composable("plan/progs/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
        ProgramEditor(vm, data, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
    }
    composable("plan/blocks") { BlocksEditor(vm, data) { nav.popBackStack() } }
    composable("plan/metrics") { MetricsEditor(vm, data) { nav.popBackStack() } }
    composable("plan/tracks") { TracksEditor(vm, data) { nav.popBackStack() } }
    composable("plan/setup") { SetupScreen(vm, data, today) { nav.popBackStack() } }
}

// ---------- обзор ----------

@Composable
internal fun PlanOverview(vm: TrainingViewModel, data: TrainingData, today: String, go: (String) -> Unit) {
    val plan = data.plan
    val di = Dates.dayIndex(today)
    var sel by rememberSaveable { mutableIntStateOf(di) }
    TabColumn {
        HeroHeader(plan.name, subtitle = "План тренировок", icon = "calendar")
        PlanStatusCard(vm, data, today)
        SectionTitle("Неделя")
        val letters = ProgramLetters.display(data.programs)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            plan.days.forEachIndexed { i, d ->
                val pr = if (d.k == "gym" && d.prog.isNotEmpty()) data.programs.firstOrNull { it.id == d.prog } else null
                val src = remember { MutableInteractionSource() }
                Surface(
                    onClick = { sel = i }, interactionSource = src, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).pressScale(src, 0.92f).semantics {
                        contentDescription = "${Texts.DAY_NAMES[i]}: ${Texts.dayLabel(d, data.programs)}" + if (i == di) ", сегодня" else ""
                    },
                    color = if (sel == i) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else DiaryTheme.extras.glass,
                    border = BorderStroke(if (i == di) 2.dp else 1.dp, if (i == di) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Texts.DAY_NAMES[i], style = MaterialTheme.typography.labelLarge)
                        Icon(DiaryIcons.get(Texts.dayIcon(d.k)), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(pr?.let { letters[it.id] } ?: " ", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        val sd = plan.days[sel]
        Text(
            "${Texts.DAY_NAMES[sel]} · ${Texts.dayLabel(sd, data.programs)}" + if (sd.note.isNotEmpty()) " — ${sd.note}" else "",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
        )
        SectionTitle("Цели недели")
        val c = Logic.weekCounts(data.sessions, Dates.weekStart(today))
        GoalRow("gym", MaterialTheme.colorScheme.primary, "Зал", c.gym, WEEKLY_GOAL, WEEKLY_GOAL.toString())
        plan.tracks.filter { it.goal > 0 }.forEach { GoalRow(it.ic, DiaryTheme.extras.home, it.name, c.t[it.id] ?: 0, it.goal, it.goalText.ifEmpty { it.goal.toString() }) }
        if (plan.rules.isNotEmpty()) Accordion("Правила") { plan.rules.forEach { r -> Text(r.t, style = MaterialTheme.typography.titleSmall); Muted(r.d) } }
        if (plan.protein) {
            val kg = Logic.latestWeight(data.wlog)
            Accordion("Питание") { Text("Белок: ${Js.round(kg * 1.6).toInt()}–${Js.round(kg * 2).toInt()} г в день (1,6–2 г на кг, вес ${Js.fmt(kg)} кг)") }
        }
        SectionTitle("Настроить")
        listOf(
            Triple("plan/days", "Дни недели", "home"), Triple("plan/progs", "Программы тренировок", "gym"), Triple("plan/blocks", "Блоки и разгрузка", "flame"),
            Triple("plan/metrics", "Замеры", "target"), Triple("plan/tracks", "Занятия дома", "star"), Triple("plan/setup", "Старт и шаблоны", "check"),
        ).forEach { (route, label, ic) ->
            val src = remember { MutableInteractionSource() }
            Surface(
                onClick = { go(route) }, interactionSource = src, shape = RoundedCornerShape(18.dp), color = DiaryTheme.extras.glass,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).pressScale(src, 0.97f),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(ic, MaterialTheme.colorScheme.primary)
                        Text(label, style = MaterialTheme.typography.titleSmall)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun GoalRow(icon: String, color: androidx.compose.ui.graphics.Color, name: String, n: Int, goal: Int, goalText: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(DiaryIcons.get(icon), null, tint = color, modifier = Modifier.size(18.dp))
        Text(name, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
        MeterBar(if (goal > 0) n.toFloat() / goal else 0f, "$name: $n из $goalText", Modifier.weight(1f), color)
        Muted("$n/$goalText")
    }
}

@Composable
fun Accordion(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    DiaryCard(onClick = { open = !open }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MinTouch).semantics { contentDescription = title + if (open) ", развёрнуто" else ", свёрнуто" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, tween(260), label = "accArrow")
            Text("⌄", color = MaterialTheme.colorScheme.primary, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        Reveal(open) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanStatusCard(vm: TrainingViewModel, data: TrainingData, today: String) {
    val plan = data.plan
    val total = Logic.planTotal(plan)
    val w = Logic.planWeek(plan, today)
    if (plan.start.isEmpty() || w == null) {
        DiaryCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(DiaryIcons.get("target"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Column { Text("Старт плана", style = MaterialTheme.typography.titleMedium); Muted("Выберите понедельник первой недели") }
            }
            PrimaryButton("Начать с этой недели", { vm.startPlanThisWeek() })
            var pick by rememberSaveable { mutableStateOf(false) }
            SecondaryButton("Выбрать другую дату", { pick = true })
            if (pick) StartDateDialog(onDismiss = { pick = false }, onPick = { vm.setPlanStart(it); pick = false })
        }
        return
    }
    DiaryCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                w < 1 -> {
                    val d0 = Dates.daysBetween(today, Dates.weekStart(plan.start))
                    RingIndicator(0f, d0.toString(), Js.plural(d0, "день", "дня", "дней"))
                    Text("До старта", style = MaterialTheme.typography.titleMedium)
                }
                w > total -> {
                    RingIndicator(1f, total.toString(), "недель", color = DiaryTheme.extras.cardio)
                    Column { Text("План завершён", style = MaterialTheme.typography.titleMedium); Muted("Сделайте финальные замеры") }
                }
                else -> {
                    val b = Logic.blockOf(plan, w)
                    RingIndicator(w.toFloat() / total, w.toString(), "из $total")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(b.name, style = MaterialTheme.typography.titleMedium)
                        if (b.text.isNotEmpty()) Muted(b.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (Logic.isDeload(plan, w)) Badge("Разгрузка", DiaryTheme.extras.cardio)
                            if (Logic.isTest(plan, w)) Badge("Тесты", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            QuietButton("Выбрать", {
                state.selectedDateMillis?.let { ms ->
                    onPick(Dates.format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()))
                } ?: onDismiss()
            })
        },
        dismissButton = { QuietButton("Отмена", onDismiss) },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) { DatePicker(state, colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) }
}

// ---------- дни недели ----------

@Composable
private fun DaysEditor(vm: TrainingViewModel, data: TrainingData, back: () -> Unit) {
    val plan = data.plan
    TabColumn {
        BackLink("План", back)
        ScreenTitle("Дни недели")
        val letters = ProgramLetters.display(data.programs)
        plan.days.forEachIndexed { i, d ->
            DiaryCard {
                Text(Texts.DAY_NAMES[i], style = MaterialTheme.typography.titleMedium)
                IconTiles(
                    Texts.DAY_KINDS.map { (k, name) -> TileOption(k, Texts.dayIcon(k), name, dayColor(k)) },
                    d.k, { k -> vm.updatePlan { PlanEdit.setDayKind(it, i, k) } },
                )
                Reveal(d.k == "gym") {
                    Muted("Программа: " + (data.programs.firstOrNull { it.id == d.prog }?.title ?: "по очереди"))
                    LetterPicker(
                        listOf(LetterOption("", "↻", "По очереди")) + data.programs.map { LetterOption(it.id, letters.getValue(it.id), it.title) },
                        d.prog, { p -> vm.updatePlan { PlanEdit.setDayProg(it, i, p) } }, tile = 52.dp,
                    )
                }
                DebouncedField("Заметка к дню, по желанию", d.note, { n -> vm.updatePlan { PlanEdit.setDayNote(it, i, n) } }, maxLength = 120)
            }
        }
        SectionTitle("Правила")
        Muted("Строка: «Заголовок | текст»")
        DebouncedField("Правила плана", PlanEdit.rulesToText(plan.rules), { t -> vm.updatePlan { it.copy(rules = PlanEdit.rulesFromText(t)) } }, singleLine = false, minLines = 3)
        CheckRow("Подсказки после волейбола на экране «Сегодня»", plan.volleyHints) { v -> vm.updatePlan { it.copy(volleyHints = v) } }
    }
}

/** Строка настройки с переключателем «вкл/выкл». */
@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) = ToggleRow(text, checked, onChange)

/** Цвет типа дня: зал, дом, волейбол, отдых. */
@Composable
private fun dayColor(kind: String) = when (kind) {
    "gym" -> MaterialTheme.colorScheme.primary
    "home" -> DiaryTheme.extras.home
    "vb" -> DiaryTheme.extras.volley
    else -> DiaryTheme.extras.friends
}

// ---------- блоки ----------

@Composable
private fun BlocksEditor(vm: TrainingViewModel, data: TrainingData, back: () -> Unit) {
    val plan = data.plan
    TabColumn {
        BackLink("План", back)
        ScreenTitle("Блоки и разгрузка")
        Muted("Всего недель: ${Logic.planTotal(plan)}")
        plan.blocks.forEachIndexed { i, b ->
            DiaryCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Блок ${i + 1}", style = MaterialTheme.typography.titleMedium)
                    Row {
                        QuietButton("↑", { vm.updatePlan { PlanEdit.moveBlock(it, i, -1) } }, enabled = i > 0, modifier = Modifier.semantics { contentDescription = "Выше" })
                        QuietButton("↓", { vm.updatePlan { PlanEdit.moveBlock(it, i, 1) } }, enabled = i < plan.blocks.size - 1, modifier = Modifier.semantics { contentDescription = "Ниже" })
                        QuietButton("Удалить", { vm.updatePlan { PlanEdit.removeBlock(it, i) } }, enabled = plan.blocks.size > 1)
                    }
                }
                DebouncedField("Название блока", b.name, { n -> vm.updatePlan { PlanEdit.editBlock(it, i, name = n) } }, maxLength = 40)
                Stepper("Недель", b.weeks.toString(), { vm.updatePlan { PlanEdit.stepBlockWeeks(it, i, -1) } }, { vm.updatePlan { PlanEdit.stepBlockWeeks(it, i, 1) } })
                DebouncedField("Чем занимаетесь в этом блоке", b.text, { t -> vm.updatePlan { PlanEdit.editBlock(it, i, text = t) } }, singleLine = false, minLines = 2, maxLength = 300)
            }
        }
        SecondaryButton("+ Добавить блок", { vm.updatePlan { PlanEdit.addBlock(it) } })
        SectionTitle("Разгрузка и тесты")
        DiaryCard {
            Stepper("Разгрузка каждые, недель (0 — нет)", plan.deloadEvery.toString(), { vm.updatePlan { PlanEdit.stepDeload(it, -1) } }, { vm.updatePlan { PlanEdit.stepDeload(it, 1) } })
            Stepper("Тест каждые, недель (0 — нет)", plan.testEvery.toString(), { vm.updatePlan { PlanEdit.stepTest(it, -1) } }, { vm.updatePlan { PlanEdit.stepTest(it, 1) } })
        }
    }
}

// ---------- замеры ----------

@Composable
private fun MetricsEditor(vm: TrainingViewModel, data: TrainingData, back: () -> Unit) {
    var askDelete by rememberSaveable { mutableStateOf<String?>(null) }
    TabColumn {
        BackLink("План", back)
        ScreenTitle("Замеры")
        data.plan.metrics.forEach { m ->
            DiaryCard {
                DebouncedField("Название показателя", m.name, { v -> vm.updatePlan { PlanEdit.editMetric(it, m.id, name = v) } }, maxLength = 60)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebouncedField("Единица", m.unit, { v -> vm.updatePlan { PlanEdit.editMetric(it, m.id, unit = v) } }, Modifier.weight(1f), maxLength = 20)
                    DebouncedField("Цель числом", m.goal?.let { Js.fmt(it) } ?: "", { v -> vm.updatePlan { PlanEdit.editMetric(it, m.id, goal = v) } }, Modifier.weight(1f), decimal = true)
                }
                DebouncedField("Цель словами, по желанию", m.goalText, { v -> vm.updatePlan { PlanEdit.editMetric(it, m.id, goalText = v) } }, maxLength = 80)
                SegmentedControl(listOf(1 to "Больше — лучше", -1 to "Меньше — лучше"), m.dir, { d -> vm.updatePlan { PlanEdit.editMetric(it, m.id, dir = d) } })
                DebouncedField("Как замерять, по желанию", m.how, { v -> vm.updatePlan { PlanEdit.editMetric(it, m.id, how = v) } }, maxLength = 200)
                if (askDelete == m.id) ConfirmBox("Удалить «${m.name}» вместе с историей замеров?", "Да, удалить", { vm.deleteMetric(m.id); askDelete = null }, { askDelete = null }, cancelLabel = "Нет")
                else QuietButton("Удалить показатель", { askDelete = m.id })
            }
        }
        SecondaryButton("+ Новый показатель", { vm.updatePlan { it.copy(metrics = it.metrics + PlanEdit.newMetric()) } })
    }
}

// ---------- домашние занятия ----------

@Composable
private fun TracksEditor(vm: TrainingViewModel, data: TrainingData, back: () -> Unit) {
    val plan = data.plan
    var askDelete by rememberSaveable { mutableStateOf<String?>(null) }
    TabColumn {
        BackLink("План", back)
        ScreenTitle("Занятия дома")
        plan.tracks.forEach { tr ->
            fun edit(f: (ru.diary.training.domain.Track) -> ru.diary.training.domain.Track) = vm.updatePlan { PlanEdit.setTrack(it, tr.id, f) }
            DiaryCard {
                DebouncedField("Название занятия", tr.name, { v -> edit { it.copy(name = v.take(60)) } }, maxLength = 60)
                SegmentedControl(Texts.TRACK_KIND.map { it.key to it.value }, tr.kind, { k -> edit { it.copy(kind = k) } })
                Stepper("Цель, дней в неделю (0 — нет)", tr.goal.toString(), { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.Goal, -1) } }, { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.Goal, 1) } })
                DebouncedField("Цель словами, например 5–6", tr.goalText, { v -> edit { it.copy(goalText = v.take(20)) } }, maxLength = 20)
                if (tr.kind == "timed") {
                    Stepper("Минут по умолчанию", tr.dur.toString(), { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.Dur, -1) } }, { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.Dur, 1) } })
                    DebouncedField("Описание, советы", tr.intro, { v -> edit { it.copy(intro = v.take(600)) } }, singleLine = false, minLines = 2, maxLength = 600)
                    Muted("Шаги (лестница прогресса), по строке: «Название | описание»")
                    DebouncedField("Шаги", PlanEdit.stepsToText(tr.steps), { v -> edit { it.copy(steps = PlanEdit.stepsFromText(v)) } }, singleLine = false, minLines = 3)
                    Muted("Чек-лист упражнений, по строке: «Название | детали | примечание»")
                    DebouncedField("Чек-лист", PlanEdit.itemsToText(tr.items), { v -> edit { it.copy(items = PlanEdit.itemsFromText(v)) } }, singleLine = false, minLines = 3)
                } else {
                    Muted("Показатель-максимум")
                    ChoiceChips(listOf("" to "Свой максимум вручную") + plan.metrics.map { it.id to it.name }, tr.metric, { id ->
                        vm.updatePlan { p -> PlanEdit.setTrack(p, tr.id) { PlanEdit.setTrackMetric(it, id, p, data.maxes) } }
                    })
                    Muted("Вместе с (подход добавляется в оба)")
                    ChoiceChips(listOf("" to "Ничего") + plan.metrics.filter { it.id != tr.metric && it.unit == "раз" }.map { it.id to it.name }, tr.pair, { id ->
                        vm.updatePlan { p -> PlanEdit.setTrack(p, tr.id) { PlanEdit.setTrackPair(it, id, p, data.maxes) } }
                    })
                    Stepper("Подход от, % максимума", tr.pctLo.toString(), { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.PctLo, -1) } }, { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.PctLo, 1) } })
                    Stepper("до, %", tr.pctHi.toString(), { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.PctHi, -1) } }, { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.PctHi, 1) } })
                    Stepper("Подходов в день", tr.dayTarget.toString(), { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.DayTarget, -1) } }, { edit { PlanEdit.stepTrack(it, PlanEdit.TrackField.DayTarget, 1) } })
                    DebouncedField("Совет, по желанию", tr.tip, { v -> edit { it.copy(tip = v.take(500)) } }, singleLine = false, minLines = 2, maxLength = 500)
                }
                if (askDelete == tr.id) ConfirmBox("Удалить «${tr.name}»? Прошлые записи останутся в истории.", "Да, удалить", { vm.deleteTrack(tr.id); askDelete = null }, { askDelete = null }, cancelLabel = "Нет")
                else QuietButton("Удалить занятие", { askDelete = tr.id })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("+ По времени", { vm.updatePlan { it.copy(tracks = it.tracks + PlanEdit.newTrack("timed")) } })
            SecondaryButton("+ Подходы за день", { vm.updatePlan { it.copy(tracks = it.tracks + PlanEdit.newTrack("sets")) } })
        }
    }
}

// ---------- старт и шаблоны ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(vm: TrainingViewModel, data: TrainingData, today: String, back: () -> Unit) {
    val plan = data.plan
    var ask by rememberSaveable { mutableStateOf<String?>(null) }   // "tpl:<id>" или "del:<id>"
    var tname by rememberSaveable { mutableStateOf("") }
    var pick by rememberSaveable { mutableStateOf(false) }
    TabColumn {
        BackLink("План", back)
        ScreenTitle("Старт и шаблоны")
        DiaryCard {
            DebouncedField("Название плана", plan.name, { v -> vm.updatePlan { it.copy(name = v.take(60)) } }, maxLength = 60)
            Muted(if (plan.start.isNotEmpty()) "Понедельник первой недели: " + Dates.weekStart(plan.start) else "Старт не задан")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Начать с этой недели", { vm.startPlanThisWeek() })
                SecondaryButton("Другая дата", { pick = true })
            }
            if (plan.start.isNotEmpty()) QuietButton("Сбросить старт", { vm.clearPlanStart() })
            CheckRow("Показывать норму белка (1,6–2 г на кг веса)", plan.protein) { v -> vm.updatePlan { it.copy(protein = v) } }
        }
        if (pick) StartDateDialog(onDismiss = { pick = false }, onPick = { vm.setPlanStart(it); pick = false })

        SectionTitle("Шаблоны")
        Muted("Заменяют дни, блоки, замеры и домашние занятия. История и программы остаются.")
        val a = ask
        if (a != null && a.startsWith("del:")) {
            val id = a.removePrefix("del:")
            ConfirmBox("Удалить шаблон «${Transfer.templateName(data, id)}»? Текущий план не изменится.", "Да, удалить", { vm.deleteTemplate(id); ask = null }, { ask = null }, cancelLabel = "Нет")
        } else if (a != null && a.startsWith("tpl:")) {
            val id = a.removePrefix("tpl:")
            val tm = Transfer.templateMake(data, id)
            val np = tm?.programs?.size ?: 0
            ConfirmBox(
                "Заменить текущий план шаблоном «${Transfer.templateName(data, id)}»?" + if (np > 0) " Программы шаблона ($np) заменят одноимённые (A, B, C)." else "",
                "Да, заменить", { vm.applyTemplate(id); ask = null }, { ask = null }, cancelLabel = "Нет",
            )
        } else {
            BuiltinData.builtinTemplates.forEach { b ->
                val src = remember { MutableInteractionSource() }
                Surface(
                    onClick = { ask = "tpl:" + b.id }, interactionSource = src, shape = RoundedCornerShape(18.dp), color = DiaryTheme.extras.glass,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(src, 0.97f),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(b.name, style = MaterialTheme.typography.titleSmall); Muted(b.sub) }
                        Text("›")
                    }
                }
            }
        }
        SectionTitle("Мои шаблоны")
        if (data.templates.isEmpty()) {
            Muted("Пока пусто. Настройте план и сохраните его как шаблон: дни, блоки, замеры, домашние занятия и используемые программы.")
        } else data.templates.forEach { t ->
            DiaryCard {
                Text(t.name, style = MaterialTheme.typography.titleSmall)
                Muted("блоков: ${t.plan.blocks.size}, программ: ${t.programs.size}, замеров: ${t.plan.metrics.size}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Применить", { ask = "tpl:u:" + t.id })
                    QuietButton("Удалить", { ask = "del:u:" + t.id }, Modifier.semantics { contentDescription = "Удалить шаблон ${t.name}" })
                }
            }
        }
        DiaryCard {
            LabeledField("Название нового шаблона", tname, { tname = it }, maxLength = 60, placeholder = "Например, Для друга")
            SecondaryButton("Сохранить текущий план как шаблон", { vm.saveTemplate(tname); tname = "" })
        }
    }
}

// ---------- программы ----------

@Composable
private fun ProgramsScreen(vm: TrainingViewModel, data: TrainingData, onBack: () -> Unit, onEdit: (String) -> Unit) {
    var newOpen by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    val catalog = ExerciseCatalog(data.customEx)
    TabColumn {
        BackLink("План", onBack)
        ScreenTitle("Программы")
        Muted("Программы с отметкой «в очереди» чередуются сами после каждой тренировки. Остальные (например, домашние) выбираются на главной.")
        if (data.programs.isEmpty()) Muted("Программ пока нет. Создайте первую.")
        val letters = ProgramLetters.display(data.programs)
        data.programs.forEach { p ->
            DiaryCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LetterBadge(letters.getValue(p.id))
                    Column(Modifier.weight(1f)) {
                        Text(ProgramLetters.split(p.title).second, style = MaterialTheme.typography.titleMedium)
                        if (p.rot) Muted("в очереди: чередуется с другими")
                    }
                }
                Muted(
                    if (p.items.isEmpty()) "Пока без упражнений"
                    else "${p.items.size} ${Js.plural(p.items.size, "упражнение", "упражнения", "упражнений")}: " + p.items.joinToString(", ") { catalog.nameOf(it.id) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Начать", { vm.startWorkout(p.id) })
                    SecondaryButton("Изменить", { onEdit(p.id) })
                    QuietButton("Копировать", { vm.duplicateProgram(p.id) })
                }
            }
        }
        SecondaryButton("+ Новая программа", { newOpen = true })
        if (newOpen) DiarySheet({ newOpen = false }, title = "Новая программа", icon = "gym") { close ->
            LabeledField("Название", title, { title = it }, maxLength = 60, placeholder = "Например, дома без инвентаря")
            PrimaryButton("Создать", { vm.createProgram(title)?.let { id -> title = ""; close(); onEdit(id) } }, Modifier.fillMaxWidth())
        }
        Callout("Тренировка без программы. Кнопка «Своя тренировка» на вкладке «Сегодня» открывает пустую тренировку: упражнения добавляете по ходу, и всё записывается в историю.")
    }
}

/** Буква программы на градиентной плашке. */
@Composable
private fun LetterBadge(letter: String) {
    val x = DiaryTheme.extras
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).background(x.buttonBrush, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center,
    ) { Text(letter, style = MaterialTheme.typography.titleLarge, color = x.onButton) }
}

@Composable
private fun Callout(text: String) = ru.diary.core.ui.Callout { Text(text, style = MaterialTheme.typography.bodySmall) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramEditor(vm: TrainingViewModel, data: TrainingData, id: String, back: () -> Unit) {
    val p: Program? = data.programs.firstOrNull { it.id == id }
    if (p == null) { LaunchedBack(back); return }
    val catalog = ExerciseCatalog(data.customEx)
    var pick by rememberSaveable { mutableStateOf(false) }
    var ask by rememberSaveable { mutableStateOf<String?>(null) }
    var editIdx by rememberSaveable { mutableStateOf<Int?>(null) }
    TabColumn {
        BackLink("Все программы", back)
        val (letter, name) = ProgramLetters.split(p.title)
        DiaryCard {
            Text("Буква на главном экране", style = MaterialTheme.typography.titleSmall)
            SegmentedControl(
                ProgramLetters.CHOICES.map { it to it.ifEmpty { "—" } }, letter,
                { l -> vm.updateProgram(id) { it.copy(title = ProgramLetters.join(l, ProgramLetters.split(it.title).second)) } },
            )
            Muted(if (letter.isEmpty()) "Без буквы на плашке будет первая буква названия." else "На главном экране программа выбирается плашкой «$letter».")
            DebouncedField("Название", name, { t -> vm.updateProgram(id) { it.copy(title = ProgramLetters.join(ProgramLetters.split(it.title).first, t)) } }, maxLength = 56)
            CheckRow("В очереди: чередуется с другими такими программами", p.rot) { v -> vm.updateProgram(id) { it.copy(rot = v) } }
        }
        SectionTitle("Упражнения")
        if (p.items.isEmpty()) Muted("Пока пусто. Добавьте первое упражнение.")
        // Краткий список: название и схема подходов; настройка упражнения — во всплывающей панели.
        p.items.forEachIndexed { i, it0 ->
            val d = catalog.defOf(it0.id)
            val sfx = if (d?.kind == "time") " с" else ""
            SheetRow(
                d?.name ?: it0.id, { editIdx = i }, subtitle = "${it0.sets} × " + (if (it0.lo == it0.hi) "${it0.lo}" else "${it0.lo}–${it0.hi}") + sfx,
                leading = { NumberBadge(i + 1) },
            )
        }
        val ei = editIdx
        if (ei != null && ei in p.items.indices) {
            val it0 = p.items[ei]
            val i = ei
            val d = catalog.defOf(it0.id)
            val unit = if (d?.kind == "time") "Секунды" else "Повторы"
            val st = if (d?.kind == "time") 5 else 1
            DiarySheet({ editIdx = null }, title = d?.name ?: it0.id, subtitle = "Упражнение ${i + 1} из ${p.items.size}", icon = "gym") { close ->
                Stepper("Подходы", it0.sets.toString(),
                    { vm.updateProgram(id) { q -> q.copy(items = q.items.mapIndexed { k, x -> if (k == i) x.copy(sets = Js.clamp(x.sets - 1, 1, 10)) else x }) } },
                    { vm.updateProgram(id) { q -> q.copy(items = q.items.mapIndexed { k, x -> if (k == i) x.copy(sets = Js.clamp(x.sets + 1, 1, 10)) else x }) } })
                Stepper("$unit от", it0.lo.toString(),
                    { vm.updateProgram(id) { q -> q.copy(items = q.items.mapIndexed { k, x -> if (k == i) Js.clamp(x.lo - st, 1, 999).let { lo -> x.copy(lo = lo, hi = maxOf(x.hi, lo)) } else x }) } },
                    { vm.updateProgram(id) { q -> q.copy(items = q.items.mapIndexed { k, x -> if (k == i) Js.clamp(x.lo + st, 1, 999).let { lo -> x.copy(lo = lo, hi = maxOf(x.hi, lo)) } else x }) } })
                Stepper("до", it0.hi.toString(),
                    { vm.updateProgram(id) { q -> q.copy(items = q.items.mapIndexed { k, x -> if (k == i) Js.clamp(x.hi - st, 1, 999).let { hi -> x.copy(hi = hi, lo = minOf(x.lo, hi)) } else x }) } },
                    { vm.updateProgram(id) { q -> q.copy(items = q.items.mapIndexed { k, x -> if (k == i) Js.clamp(x.hi + st, 1, 999).let { hi -> x.copy(hi = hi, lo = minOf(x.lo, hi)) } else x }) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("↑ Выше", { vm.updateProgram(id) { q -> q.copy(items = swap(q.items, i, i - 1)) }; editIdx = i - 1 }, enabled = i > 0)
                    SecondaryButton("↓ Ниже", { vm.updateProgram(id) { q -> q.copy(items = swap(q.items, i, i + 1)) }; editIdx = i + 1 }, enabled = i < p.items.size - 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Готово", close)
                    DangerButton("Убрать", { close(); vm.updateProgram(id) { q -> q.copy(items = q.items.filterIndexed { k, _ -> k != i }) } })
                }
            }
        }
        SecondaryButton("+ Добавить упражнение", { pick = true })
        if (pick) {
            ExercisePickerSheet(vm, data, p.items.map { it.id }, onDismiss = { pick = false }) { d ->
                vm.updateProgram(id) { q -> q.copy(items = q.items + ru.diary.training.domain.ProgramItem(d.id, d.sets, d.lo, d.hi)) }
                vm.message("Добавлено: ${d.name}")
            }
        }
        SectionTitle("Разминка")
        DebouncedField("По одному пункту в строке", p.warm.joinToString("\n"), { t ->
            vm.updateProgram(id) { it.copy(warm = t.split('\n').map { l -> l.trim().take(120) }.filter { l -> l.isNotEmpty() }.take(12)) }
        }, singleLine = false, minLines = 5)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (ask) {
                "del" -> ConfirmBox("Удалить программу «${p.title}»? История тренировок останется.", "Да, удалить", { vm.deleteProgram(id); back() }, { ask = null }, cancelLabel = "Нет")
                "reset" -> ConfirmBox("Вернуть исходные упражнения и разминку?", "Да, вернуть", { vm.resetProgram(id); ask = null }, { ask = null }, cancelLabel = "Нет")
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Начать эту тренировку", { vm.startWorkout(id) })
                    if (id in BuiltinData.originalProgramIds) SecondaryButton("Вернуть исходную", { ask = "reset" })
                    DangerButton("Удалить", { ask = "del" }, enabled = data.programs.size > 1)
                }
            }
        }
    }
}

@Composable
private fun LaunchedBack(back: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { back() }
}

private fun <T> swap(l: List<T>, a: Int, b: Int): List<T> =
    if (a !in l.indices || b !in l.indices) l else l.toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
