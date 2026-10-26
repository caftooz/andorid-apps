package ru.diary.training.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import ru.diary.core.ui.pressClickable
import ru.diary.core.ui.CheckItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.diary.core.charts.DotRow
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.ui.BackLink
import ru.diary.core.ui.DebouncedField
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.DiaryIcons
import ru.diary.core.ui.MinTouch
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.util.Js
import ru.diary.training.domain.Logic
import ru.diary.core.ui.IconBubble
import ru.diary.core.ui.SheetRow
import ru.diary.training.domain.Guides
import ru.diary.core.ui.ChoiceChips
import ru.diary.training.domain.PlanEdit
import ru.diary.training.domain.Track
import ru.diary.training.domain.TrainingData

/** Домашнее занятие: лестница шагов, чек-лист, запись занятия или подходы за день. */
@Composable
fun TrackScreen(vm: TrainingViewModel, data: TrainingData, today: String, id: String, onBack: () -> Unit, onGoto: (String) -> Unit) {
    val tr: Track? = data.plan.tracks.firstOrNull { it.id == id }
    if (tr == null) { LaunchedEffect(Unit) { onBack() }; return }
    val n = Logic.weekCounts(data.sessions, Dates.weekStart(today)).t[tr.id] ?: 0
    var logOpen by rememberSaveable { mutableStateOf(false) }
    var done by rememberSaveable { mutableStateOf(setOf<String>()) }
    // Открытая инструкция: «t:» — общая часть занятия, «i:<название>» — шаг или упражнение.
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    guide?.let { g ->
        val (title, text) = if (g == "t:") "Как заниматься" to Guides.forTrack(tr.id) else g.removePrefix("i:") to Guides.forItem(g.removePrefix("i:"))
        if (text != null) GuideSheet(title, text, tr.ic) { guide = null }
    }

    TabColumn {
        BackLink("Сегодня", onBack)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(DiaryIcons.get(tr.ic), null, tint = DiaryTheme.extras.home, modifier = Modifier.size(40.dp))
            Column {
                Text(tr.name, style = MaterialTheme.typography.headlineSmall)
                if (tr.goal > 0) {
                    DotRow(n, tr.goal)
                    Muted("$n из ${tr.goalText.ifEmpty { tr.goal.toString() }} дн.")
                } else Muted("$n дн. на этой неделе")
            }
        }
        if (Guides.forTrack(tr.id) != null) {
            SheetRow("Как заниматься", { guide = "t:" }, subtitle = "Разминка, основы и советы", leading = { IconBubble("star", DiaryTheme.extras.home) })
        } else if (tr.intro.isNotEmpty()) Accordion("О занятии") { Muted(tr.intro) }
        if (tr.steps.isNotEmpty()) {
            DiaryCard {
                tr.steps.forEachIndexed { i, s ->
                    val cur = i + 1 == tr.cur
                    val past = i + 1 < tr.cur
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MinTouch).clip(RoundedCornerShape(14.dp)).pressClickable(scale = 0.98f) {
                            vm.updatePlan { p -> p.copy(tracks = p.tracks.map { if (it.id == tr.id) it.copy(cur = i + 1) else it }) }
                        }.semantics { contentDescription = "Шаг ${i + 1}: ${s.n}" + if (cur) ", текущий" else ", сделать текущим" },
                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (past) "✓" else "${i + 1}", style = MaterialTheme.typography.titleMedium,
                            color = if (cur || past) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(s.n, style = if (cur) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
                            if (cur && s.t.isNotEmpty()) Muted(s.t)
                            if (Guides.forItem(s.n) != null) GuideLink({ guide = "i:" + s.n })
                        }
                    }
                }
            }
        }
        if (tr.kind == "timed") {
            if (tr.items.isNotEmpty()) DiaryCard {
                tr.items.forEachIndexed { i, it0 ->
                    val key = "${tr.id}:$i"
                    val sub = listOf(it0.d, it0.t).filter { s -> s.isNotEmpty() }.joinToString(" · ")
                    Column {
                        CheckItem(it0.n, key in done, { done = if (key in done) done - key else done + key })
                        if (sub.isNotEmpty()) Muted(sub, Modifier.padding(start = 44.dp))
                        if (Guides.forItem(it0.n) != null) GuideLink({ guide = "i:" + it0.n }, Modifier.padding(start = 32.dp))
                    }
                }
            }
            PrimaryButton("Записать занятие", { logOpen = true })
            if (logOpen) QuickLogSheet("t:" + tr.id, vm, data) { logOpen = false }
        } else {
            val (lo, hi) = Logic.setReps(tr.base, tr.pctLo, tr.pctHi)
            val sl = Logic.setsToday(data.sessions, tr.id, today)
            val metric = data.plan.metrics.firstOrNull { it.id == tr.metric }
            val pairM = data.plan.metrics.firstOrNull { it.id == tr.pair }
            fun edit(f: (Track) -> Track) = vm.updatePlan { p -> PlanEdit.setTrack(p, tr.id, f) }
            DiaryCard {
                DebouncedField(
                    "Ваш максимум" + (metric?.let { ": " + it.name.lowercase() } ?: ""), tr.base.toString(),
                    { t -> t.toIntOrNull()?.takeIf { it in 1..999 }?.let { b -> edit { it.copy(base = b) } } },
                    numeric = true,
                )
                if (pairM != null) DebouncedField(
                    "Максимум: " + pairM.name.lowercase(), tr.base2.toString(),
                    { t -> t.toIntOrNull()?.takeIf { it in 1..999 }?.let { b -> edit { it.copy(base2 = b) } } },
                    numeric = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("gym", if (lo == hi) "$lo" else "$lo–$hi", "повт. в подходе", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    Tile("flame", "${tr.dayTarget}", "подходов в день", DiaryTheme.extras.volley, Modifier.weight(1f))
                    Tile("check", "${sl.sum()}", "повт. сегодня", DiaryTheme.extras.cardio, Modifier.weight(1f))
                }
                if (pairM != null) {
                    val (lo2, hi2) = Logic.setReps(tr.base2, tr.pctLo, tr.pctHi)
                    val sl2 = Logic.pairSetsToday(data.sessions, tr.id, today)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("gym", if (lo2 == hi2) "$lo2" else "$lo2–$hi2", pairM.name.lowercase() + " в подходе", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        Tile("check", "${sl2.sum()}", pairM.name.lowercase() + " сегодня", DiaryTheme.extras.cardio, Modifier.weight(1f))
                    }
                }
                DotRow(sl.size, tr.dayTarget)
                val (h1, h2) = Logic.trackReps(tr, data.plan)
                if (h2 != null) Muted("Одна кнопка — подход в оба: " + Texts.pairLine(tr, data.plan, h1, h2).lowercase())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(if (h2 != null) "+ подход" else "+ подход ($h1)", { vm.addTrackSet(tr.id, h1, h2) })
                    SecondaryButton("Убрать последний", { vm.undoTrackSet(tr.id) }, enabled = sl.isNotEmpty())
                }
            }
            DiaryCard {
                Text("Вместе с", style = MaterialTheme.typography.titleSmall)
                Muted("Делаете подряд второе упражнение? Выберите его — подход будет добавляться в оба сразу.")
                ChoiceChips(
                    listOf("" to "Ничего") + data.plan.metrics.filter { it.id != tr.metric && it.unit == "раз" }.map { it.id to it.name },
                    tr.pair, { id -> vm.updatePlan { p -> PlanEdit.setTrack(p, tr.id) { PlanEdit.setTrackPair(it, id, p, data.maxes) } } },
                )
            }
            if (tr.tip.isNotEmpty()) Accordion("Советы") { Muted(tr.tip) }
        }
        QuietButton("Настроить занятие", { onGoto("plan/tracks") })
    }
}

@Suppress("unused")
private fun fmt(d: Double) = Js.fmt(d)
