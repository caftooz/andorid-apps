package ru.diary.training.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.diary.core.charts.DotRow
import ru.diary.core.charts.RingIndicator
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.ui.Callout
import ru.diary.core.ui.Appear
import ru.diary.core.ui.ChoiceChips
import ru.diary.core.ui.ChoiceChipsRow
import ru.diary.core.ui.ScreenTitle
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.DiaryIcons
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.MinTouch
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.Reveal
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.ui.SectionTitle
import ru.diary.core.util.Js
import ru.diary.training.domain.Logic
import ru.diary.training.domain.Session
import ru.diary.training.domain.TrainingData
import ru.diary.training.domain.WEEKLY_GOAL

/**
 * Вкладка «Сегодня»: план на сегодня, быстрый запуск тренировки, недельная картина,
 * быстрая запись занятий без тренировки, домашние занятия, последние записи.
 */
@Composable
fun TodayScreen(
    vm: TrainingViewModel,
    data: TrainingData,
    today: String,
    onOpenTrack: (String) -> Unit,
    onGoto: (String) -> Unit,
    extraTiles: @Composable () -> Unit = {},
) {
    val plan = data.plan
    val ws = Dates.weekStart(today)
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var quickQ by rememberSaveable { mutableStateOf<String?>(null) }

    TabColumn {
        Appear(index = 0) {
            Column {
                ScreenTitle("Сегодня")
                Muted(Dates.weekdayDate(today))
            }
        }
        Appear(index = 1) { TodayPlanCard(data, today, onGoto) }
        val sel = chosen?.takeIf { id -> data.programs.any { it.id == id } }
        val progId = sel ?: Logic.todayProg(plan, data.programs, data.sessions, today)
        val prog = progId?.let { id -> data.programs.firstOrNull { it.id == id } }
        Appear(index = 2) { if (prog != null) {
            val label = when {
                sel != null -> "Выбрано"
                Logic.planDayProg(plan, data.programs, today) != null -> "Сегодня по плану"
                else -> "Следующая по порядку"
            }
            DiaryCard {
                Muted(label)
                Text(prog.title, style = MaterialTheme.typography.titleLarge)
                Muted(if (prog.items.isNotEmpty()) Logic.progBrief(prog) else "В программе пока нет упражнений. Их можно добавить в разделе «План → Программы».")
                PrimaryButton("Провести тренировку", { vm.startWorkout(prog.id) }, Modifier.fillMaxWidth().heightIn(min = 60.dp))
                ChoiceChipsRow(data.programs.map { it.id to it.title }, prog.id, { chosen = it })
                QuietButton("Своя тренировка", { vm.startWorkout(null) })
            }
        } else {
            DiaryCard {
                Text("Нет программ", style = MaterialTheme.typography.titleLarge)
                Muted("Создайте программу в разделе «План → Программы» или начните тренировку без неё.")
                PrimaryButton("Своя тренировка", { vm.startWorkout(null) }, Modifier.fillMaxWidth())
            }
        } }

        Appear(index = 3) { TodayTiles(data, today) }
        Appear(index = 4) { Column { extraTiles() } }

        SectionTitle("Записать")
        QuickButtons(quickQ) { quickQ = if (quickQ == it) null else it }
        val q = quickQ
        Reveal(q != null && q in setOf("volley", "friends", "other")) {
            if (q != null) QuickLogForm(q, data, onSave = { form -> vm.logQuick(q, form) { Texts.typeName(it, plan).lowercase() }; quickQ = null }, onCancel = { quickQ = null })
        }
        val counts = Logic.weekCounts(data.sessions, ws)
        plan.tracks.forEach { tr ->
            val key = "t:" + tr.id
            DiaryCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(DiaryIcons.get(tr.ic), null, tint = DiaryTheme.extras.home, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr.name, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.heightIn(min = MinTouch).clickable(role = Role.Button) { onOpenTrack(tr.id) }.wrapCenter(),
                        )
                        if (tr.kind == "sets") {
                            val sl = Logic.setsToday(data.sessions, tr.id, today)
                            DotRow(sl.size, tr.dayTarget)
                        } else {
                            val n = counts.t[tr.id] ?: 0
                            if (tr.goal > 0) DotRow(n, tr.goal) else Muted("$n на этой неделе")
                        }
                    }
                    if (tr.kind == "sets") {
                        val sl = Logic.setsToday(data.sessions, tr.id, today)
                        val (_, hi) = Logic.setReps(tr.base, tr.pctLo, tr.pctHi)
                        PrimaryButton("+ $hi", { vm.addTrackSet(tr.id, hi) })
                        if (sl.isNotEmpty()) QuietButton("↶", { vm.undoTrackSet(tr.id) }, Modifier.semantics { contentDescription = "Убрать последний подход" })
                    } else {
                        SecondaryButton("Записать", { quickQ = key })
                    }
                }
                Reveal(quickQ == key) {
                    QuickLogForm(key, data, onSave = { form -> vm.logQuick(key, form) { Texts.typeName(it, plan).lowercase() }; quickQ = null }, onCancel = { quickQ = null })
                }
            }
        }

        SectionTitle("Последние занятия")
        val recent = data.sessions.sortedByDescending { it.date + it.start }.take(6)
        if (recent.isEmpty()) {
            Muted("Здесь появятся ваши тренировки. Нажмите «Провести тренировку», чтобы начать с первой программы.")
        } else {
            DiaryCard {
                recent.forEach { s -> RecentRow(s, data, today) { vm.removeSession(s.id) } }
            }
        }
    }
}

@Composable
private fun RecentRow(s: Session, data: TrainingData, today: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text("${Texts.dateLabel(s.date, today)} · ${Texts.typeName(s, data.plan)}", style = MaterialTheme.typography.titleSmall)
            Muted(Texts.sessionSummary(s, data.plan) + if (s.pain.isNotEmpty()) " · болело: ${s.pain.joinToString(", ").lowercase()}" else "")
        }
        QuietButton("Удалить", onDelete, Modifier.semantics { contentDescription = "Удалить запись: ${Texts.typeName(s, data.plan)}, ${Texts.dateLabel(s.date, today)}" })
    }
}

private fun Modifier.wrapCenter(): Modifier = this

@Composable
fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickButtons(selected: String?, onClick: (String) -> Unit) {
    val items = listOf(Triple("volley", "vb", "Волейбол"), Triple("friends", "friends", "С друзьями"), Triple("other", "cardio", "Кардио"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (k, ic, label) ->
            val color = Texts.catColor(if (k == "other") "cardio" else k)
            val on = selected == k
            Surface(
                onClick = { onClick(k) },
                modifier = Modifier.weight(1f).heightIn(min = 72.dp).semantics { contentDescription = label + if (on) ", выбрано" else "" },
                shape = RoundedCornerShape(14.dp),
                color = if (on) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) color else MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(DiaryIcons.get(ic), null, tint = color, modifier = Modifier.size(24.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Форма быстрой записи: день, минуты, ккал, нагрузка, заметка; для «другого» ещё и вид активности. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickLogForm(q: String, data: TrainingData, onSave: (Logic.QuickForm) -> Unit, onCancel: () -> Unit) {
    val plan = data.plan
    val today = Dates.today()
    val yesterday = Dates.addDays(today, -1)
    var day by rememberSaveable(q) { mutableStateOf(today) }
    var dur by rememberSaveable(q) { mutableIntStateOf(Texts.quickDefault(q, plan)) }
    var durCustom by rememberSaveable(q) { mutableStateOf(false) }
    var durText by rememberSaveable(q) { mutableStateOf(dur.toString()) }
    var kcal by rememberSaveable(q) { mutableStateOf("") }
    var int by rememberSaveable(q) { mutableIntStateOf(3) }
    var note by rememberSaveable(q) { mutableStateOf("") }
    var name by rememberSaveable(q) { mutableStateOf(if (q == "other") Texts.ACT_PRESETS[0] else "") }
    var nameCustom by rememberSaveable(q) { mutableStateOf(false) }

    Callout {
        Text(Texts.quickName(q, plan), style = MaterialTheme.typography.titleMedium)
        if (q == "other") {
            val recent = data.sessions.sortedByDescending { it.date }.filter { it.type == "other" && it.title.isNotEmpty() && it.title !in Texts.ACT_PRESETS }
                .map { it.title }.distinct().take(3)
            val names = Texts.ACT_PRESETS + recent
            Muted("Что делали")
            ChoiceChips(names.map { it to it } + (CUSTOM to "✎ Своё"), if (nameCustom) CUSTOM else name, {
                if (it == CUSTOM) { nameCustom = true; name = "" } else { nameCustom = false; name = it }
            })
            if (nameCustom) LabeledField("Название", name, { name = it }, maxLength = 60, placeholder = "Например, эллипс")
        }
        Muted("День")
        ChoiceChips(listOf(today to "Сегодня", yesterday to "Вчера"), day, { day = it })
        Muted("Минут")
        ChoiceChips(Texts.quickDurations(q).map { it to it.toString() } + (-1 to "✎ Своё"), if (durCustom) -1 else dur, {
            if (it == -1) durCustom = true else { durCustom = false; dur = it }
        })
        if (durCustom) LabeledField("Своё время, минут", durText, { durText = it; it.toIntOrNull()?.takeIf { v -> v in 1..600 }?.let { v -> dur = v } }, numeric = true)
        LabeledField("Калории, ккал, по желанию", kcal, { kcal = it }, numeric = true)
        if (!q.startsWith("t:")) {
            Muted("Нагрузка")
            ChoiceChips((1..5).map { it to "$it ${Texts.INT_LABEL[it]}" }, int, { int = it })
        }
        LabeledField("Заметка, по желанию", note, { note = it }, maxLength = 300)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Записать", { onSave(Logic.QuickForm(day = day, dur = dur, int = int, note = note, name = name, kcal = kcal)) })
            SecondaryButton("Отмена", onCancel)
        }
    }
}

private const val CUSTOM = "__custom__"

@Composable
private fun TodayTiles(data: TrainingData, today: String) {
    val ws = Dates.weekStart(today)
    val wk = Logic.weekCounts(data.sessions, ws)
    val tot = Logic.weekTotals(data.sessions, ws)
    val st = Logic.streak(data.sessions, today)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("gym", "${wk.gym}/$WEEKLY_GOAL", "зал", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        Tile("cardio", "${tot.min}", "минут", DiaryTheme.extras.cardio, Modifier.weight(1f))
        Tile("flame", if (tot.kcal > 0) "${tot.kcal}" else "—", "ккал", DiaryTheme.extras.volley, Modifier.weight(1f))
        Tile("target", "$st", if (st == 1) "неделя подряд" else "нед. подряд", DiaryTheme.extras.home, Modifier.weight(1f))
    }
}

@Composable
fun Tile(icon: String, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$value $label" }, shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(DiaryIcons.get(icon), null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Карточка плана: неделя в кольце, блок, тип сегодняшнего дня, бейджи, неделя по дням. */
@Composable
private fun TodayPlanCard(data: TrainingData, today: String, onGoto: (String) -> Unit) {
    val plan = data.plan
    val w = Logic.planWeek(plan, today)
    val d = Logic.planDay(plan, today)
    val total = Logic.planTotal(plan)
    val inPlan = Logic.inPlan(plan, w)
    DiaryCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (inPlan) RingIndicator(w!!.toFloat() / total, w.toString(), "из $total")
            else Icon(DiaryIcons.get("target"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        inPlan -> Logic.blockOf(plan, w!!).name
                        w != null && w > total -> "План завершён"
                        else -> plan.name
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Muted("Сегодня: " + Texts.dayLabel(d, data.programs))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (inPlan && Logic.isDeload(plan, w!!)) Badge("Разгрузка", DiaryTheme.extras.cardio)
                    if (inPlan && Logic.isTest(plan, w!!)) Badge("Тесты", MaterialTheme.colorScheme.primary)
                }
                if (w == null) SecondaryButton("Старт плана", { onGoto("plan/setup") })
            }
        }
        WeekStrip(data, today)
        val ms = Logic.maxStatus(plan, data.maxes, today)
        if (ms.due && (plan.start.isNotEmpty() || data.maxes.isNotEmpty())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (ms.testWeek) "Неделя тестов: замерьте максимум" else if (ms.days == null) "Сделайте стартовые замеры" else "Пора обновить замеры",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                SecondaryButton("Замерить", { onGoto("progress/max") })
            }
        }
        val vn = Logic.volleyNote(plan, data.sessions, today)
        if (vn.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(DiaryIcons.get("vb"), null, tint = DiaryTheme.extras.volley, modifier = Modifier.size(18.dp))
                Text(vn, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Семь кружков недели: выполненные занятия цветом категории, остальные — иконкой типа дня по плану. */
@Composable
private fun WeekStrip(data: TrainingData, today: String) {
    val ws = Dates.weekStart(today)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) {
            val date = Dates.addDays(ws, i)
            val day = data.plan.days[i]
            val cats = data.sessions.filter { it.date == date }.map { Texts.cat(it) }.toSet()
            val have = Texts.CAT_ORDER.filter { it in cats }
            val main = have.firstOrNull()
            val isToday = date == today
            val desc = Texts.DAY_NAMES[i] + ": " + (if (have.isNotEmpty()) "сделано " + have.joinToString(", ") { Texts.CAT_NAME.getValue(it).lowercase() } else Texts.DAY_KINDS[day.k]?.lowercase()) + if (isToday) ", сегодня" else ""
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = desc },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    Texts.DAY_NAMES[i], style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val color = if (main != null) Texts.catColor(main) else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    Modifier.size(34.dp)
                        .background(if (main != null) color.copy(alpha = 0.18f) else Color.Transparent, CircleShape)
                        .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DiaryIcons.get(if (main != null) Texts.CAT_ICON.getValue(main) else Texts.dayIcon(day.k)), null, tint = color, modifier = Modifier.size(18.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.heightIn(min = 6.dp)) {
                    have.drop(1).forEach { Box(Modifier.size(5.dp).background(Texts.catColor(it), CircleShape)) }
                }
            }
        }
    }
}

@Suppress("unused")
private fun fmt(d: Double) = Js.fmt(d)
