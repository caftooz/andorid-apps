package ru.diary.training.ui

import ru.diary.core.ui.SegmentedControl
import ru.diary.core.ui.TileOption
import ru.diary.core.ui.IconTiles
import ru.diary.core.ui.HeroHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.diary.core.charts.BarColumn
import ru.diary.core.charts.BarPart
import ru.diary.core.charts.ChartPoint
import ru.diary.core.charts.Legend
import ru.diary.core.charts.LegendItem
import ru.diary.core.charts.LineChart
import ru.diary.core.charts.MeterBar
import ru.diary.core.charts.Sparkline
import ru.diary.core.charts.StackedBarChart
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.ui.Callout
import ru.diary.core.ui.ChoiceChips
import ru.diary.core.ui.DebouncedField
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.DropdownSelect
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.ui.SectionTitle
import ru.diary.core.util.Js
import ru.diary.training.domain.ExerciseCatalog
import ru.diary.training.domain.Logic
import ru.diary.training.domain.Metric
import ru.diary.training.domain.TrainingData
import ru.diary.training.domain.WEEKLY_GOAL

/** Вкладка «Прогресс»: активность, упражнения, замеры и журнал недели. */
@Composable
fun ProgressScreen(vm: TrainingViewModel, data: TrainingData, today: String, startTab: String, onGoto: (String) -> Unit, integration: TrainingIntegration) {
    var tab by rememberSaveable(startTab) { mutableStateOf(startTab.takeIf { it in setOf("train", "ex", "max", "log") } ?: "train") }
    TabColumn {
        HeroHeader("Прогресс", subtitle = "Активность, рекорды и замеры", icon = "trend")
        IconTiles(
            listOf(
                TileOption("train", "chart", "Активность"), TileOption("ex", "gym", "Рекорды"),
                TileOption("max", "target", "Замеры"), TileOption("log", "book", "Журнал"),
            ),
            tab, { tab = it },
        )
        when (tab) {
            "ex" -> ExercisesTab(data, today)
            "max" -> MaxTab(vm, data, today, onGoto)
            "log" -> LogTab(vm, data, today, integration)
            else -> ActivityTab(data, today)
        }
    }
}

// ---------- активность ----------

@Composable
private fun ActivityTab(data: TrainingData, today: String) {
    var key by rememberSaveable { mutableStateOf("min") }
    val ws0 = Dates.weekStart(today)
    val weeks = (7 downTo 0).map { i ->
        val ws = Dates.addDays(ws0, -7 * i)
        val we = Dates.addDays(ws, 6)
        val parts = linkedMapOf<String, Double>()
        data.sessions.filter { it.date in ws..we }.forEach { s ->
            val v = if (key == "kcal") (s.kcal ?: 0) else s.min
            if (v > 0) parts.merge(Texts.cat(s), v.toDouble(), Double::plus)
        }
        ws to parts
    }
    val used = data.sessions.map { Texts.cat(it) }.toSet()
    SegmentedControl(listOf("min" to "Минуты", "kcal" to "Ккал"), key, { key = it })
    val columns = weeks.map { (ws, parts) ->
        BarColumn(Dates.ddmm(ws), Texts.CAT_ORDER.mapNotNull { c -> parts[c]?.let { BarPart(it, Texts.catColor(c)) } })
    }
    val desc = (if (key == "kcal") "Калории" else "Минуты") + " по неделям, последние 8 недель: " +
        weeks.joinToString("; ") { (ws, p) -> "${Dates.ddmm(ws)} — ${Js.fmt(p.values.sum())}" }
    StackedBarChart(columns, desc)
    Legend(Texts.CAT_ORDER.filter { it in used }.map { LegendItem(Texts.CAT_NAME.getValue(it), Texts.catColor(it)) })
    val from = Dates.addDays(today, -29)
    val last = data.sessions.filter { it.date in from..today }
    val m = last.sumOf { it.min }
    val k = last.sumOf { it.kcal ?: 0 }
    Row(Modifier.fillMaxWidth().padding0(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("gym", "${last.size}", "занятий за 30 дн.", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        Tile("cardio", Js.fmt(Js.round(m / 6.0) / 10), "часов", DiaryTheme.extras.cardio, Modifier.weight(1f))
        Tile("flame", if (k > 0) "$k" else "—", "ккал", DiaryTheme.extras.volley, Modifier.weight(1f))
    }
    Muted("Ккал — ваша оценка расхода на занятиях, а не общий расход за день.")
}

private fun Modifier.padding0(): Modifier = this

// ---------- упражнения ----------

@Composable
private fun ExercisesTab(data: TrainingData, today: String) {
    val catalog = ExerciseCatalog(data.customEx)
    class Info(val name: String, val kind: String)
    val info = LinkedHashMap<String, Info>()
    data.sessions.filter { it.type == "gym" }.forEach { s ->
        s.ex.filter { it.sets.isNotEmpty() }.forEach { e ->
            val d = catalog.defOf(e.id)
            info[e.id] = if (d != null) Info(d.name, d.kind) else Info(e.name ?: e.id, e.kind ?: "w")
        }
    }
    val ids = info.keys.sortedWith { a, b -> info.getValue(a).name.compareTo(info.getValue(b).name, ignoreCase = true) }
    SectionTitle("Рабочий результат")
    if (ids.isEmpty()) {
        Muted("Появится после первой сохранённой тренировки в зале: здесь будет график рабочего веса по каждому упражнению.")
    } else {
        var sel by rememberSaveable { mutableStateOf(ids.first()) }
        val cur = if (sel in info) sel else ids.first()
        val d = info.getValue(cur)
        DropdownSelect("Упражнение", ids.map { it to info.getValue(it).name }, cur, { sel = it })
        val sess = data.sessions.filter { it.type == "gym" }.flatMap { s -> s.ex.filter { it.id == cur && it.sets.isNotEmpty() }.map { s to it } }
            .sortedBy { (s, _) -> s.date + s.start }
        fun value(w: Double, r: Int) = if (d.kind == "w") w else r.toDouble()
        val unit = if (d.kind == "w") "кг" else if (d.kind == "time") "с" else "повт."
        val pts = sess.takeLast(12).map { (s, e) -> ChartPoint(Dates.shortDate(s.date), e.sets.maxOf { value(it.w, it.r) }) }
        val best = sess.maxOf { (_, e) -> e.sets.maxOf { value(it.w, it.r) } }
        LineChart(pts, unit, "Рабочий результат: ${d.name}")
        Muted("По вертикали: лучший " + (if (d.kind == "w") "вес подхода, кг" else if (d.kind == "time") "результат, секунды" else "результат, повторы") + " за тренировку. Рекорд: ${Js.fmt(best)} $unit.")
        DiaryCard {
            sess.takeLast(5).reversed().forEach { (s, e) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Texts.dateLabel(s.date, today), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        e.sets.joinToString(", ") { st -> if (d.kind == "w") "${Js.fmt(st.w)}×${st.r}" else if (d.kind == "time") "${st.r} с" else "${st.r}" },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    SectionTitle("Занятия по неделям")
    val ws0 = Dates.weekStart(today)
    val weeks = (7 downTo 0).map { i ->
        val ws = Dates.addDays(ws0, -7 * i)
        ws to Logic.weekCounts(data.sessions, ws)
    }
    StackedBarChart(
        weeks.map { (ws, c) ->
            BarColumn(Dates.ddmm(ws), listOf(BarPart(c.gym.toDouble(), Texts.catColor("gym")), BarPart(c.volley.toDouble(), Texts.catColor("volley")), BarPart(c.friends.toDouble(), Texts.catColor("friends"))))
        },
        "Занятия по неделям за последние 8 недель: " + weeks.joinToString("; ") { (ws, c) -> "${Dates.ddmm(ws)} — ${c.gym + c.volley + c.friends}" } + ". Цель: $WEEKLY_GOAL в неделю.",
        goal = WEEKLY_GOAL.toDouble(), minMax = WEEKLY_GOAL.toDouble(),
    )
    Legend(listOf(LegendItem("Зал", Texts.catColor("gym")), LegendItem("Волейбол", Texts.catColor("volley")), LegendItem("Игры с друзьями", Texts.catColor("friends"))))
    Muted("Пунктир: цель, $WEEKLY_GOAL занятия в неделю. Подписи внизу: понедельник недели.")

    val from = Dates.addDays(today, -29)
    val inRange = data.sessions.filter { it.date in from..today }
    val gym = inRange.count { it.type == "gym" }
    val vb = inRange.count { it.type == "volley" }
    val fr = inRange.count { it.type == "friends" }
    val mins = inRange.filter { it.type == "volley" || it.type == "friends" }.sumOf { it.min }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("gym", "$gym", "в зале за 30 дней", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        Tile("vb", "$vb", "раз волейбол", DiaryTheme.extras.volley, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("friends", "$fr", "игр с друзьями", DiaryTheme.extras.friends, Modifier.weight(1f))
        Tile("cardio", Js.fmt(Js.round(mins / 60.0 * 10) / 10) + " ч", "игры за 30 дней", DiaryTheme.extras.cardio, Modifier.weight(1f))
    }
    val pains = data.sessions.filter { it.pain.isNotEmpty() && it.date >= Dates.addDays(today, -14) }.sortedByDescending { it.date }
    if (pains.isNotEmpty()) Callout {
        Text("Жалобы за 2 недели", style = MaterialTheme.typography.titleSmall)
        pains.take(5).forEach { Text("${Texts.dateLabel(it.date, today)}: ${it.pain.joinToString(", ").lowercase()}", style = MaterialTheme.typography.bodySmall) }
        Muted("Если одно и то же место болит несколько недель, покажитесь врачу или тренеру и снизьте нагрузку на это место.")
    }
}

// ---------- замеры ----------

@Composable
private fun MaxTab(vm: TrainingViewModel, data: TrainingData, today: String, onGoto: (String) -> Unit) {
    val metrics = data.plan.metrics
    if (metrics.isEmpty()) {
        Muted("Показателей для замеров пока нет. Добавьте их в разделе «План → Замеры».")
        SecondaryButton("Настроить замеры", { onGoto("plan/metrics") })
        return
    }
    val ms = Logic.maxStatus(data.plan, data.maxes, today)
    Callout { Text(ms.text, style = MaterialTheme.typography.bodySmall) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var value by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    metrics.forEach { m ->
        DiaryCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                SecondaryButton(if (open == m.id) "Скрыть" else "+ Замер", { open = if (open == m.id) null else m.id; value = ""; day = null })
            }
            MetricSummary(m, data)
            if (open == m.id) {
                if (m.how.isNotEmpty()) Muted(m.how)
                LabeledField("Результат, ${m.unit}", value, { value = it }, decimal = true)
                val y = Dates.addDays(today, -1)
                SegmentedControl(listOf(today to "Сегодня", y to "Вчера"), day ?: today, { day = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Записать", { vm.saveMax(m.id, value, day); if (value.isNotBlank()) open = null })
                    QuietButton("Отмена", { open = null })
                }
            }
        }
    }
    var chartId by rememberSaveable { mutableStateOf(metrics.first().id) }
    val cm = metrics.firstOrNull { it.id == chartId } ?: metrics.first()
    SectionTitle("График")
    DropdownSelect("Показатель", metrics.map { it.id to it.name }, cm.id, { chartId = it })
    val list = Logic.maxesOf(data.maxes, cm.id)
    if (list.isNotEmpty()) {
        LineChart(list.takeLast(12).map { ChartPoint(Dates.shortDate(it.date), it.v) }, cm.unit, cm.name, goal = cm.goal)
        Muted((if (cm.goal != null) "Пунктир: цель. " else "") + if (cm.dir < 0) "Чем меньше, тем лучше." else "")
        DiaryCard {
            list.reversed().take(10).forEach { x ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${Js.fmt(x.v)} ${cm.unit}", style = MaterialTheme.typography.titleSmall)
                        Muted(Texts.dateLabel(x.date, today))
                    }
                    QuietButton("Удалить", { vm.deleteMax(x.id) }, Modifier.androidSemanticsDesc("Удалить замер ${Js.fmt(x.v)} ${cm.unit}"))
                }
            }
        }
    } else Muted("По этому показателю замеров пока нет.")
}

private fun Modifier.androidSemanticsDesc(text: String): Modifier =
    this.then(Modifier.semantics { contentDescription = text })

@Composable
private fun MetricSummary(m: Metric, data: TrainingData) {
    val list = Logic.maxesOf(data.maxes, m.id)
    val goalText = m.goalText.ifEmpty { m.goal?.let { Js.fmt(it) } ?: "" }
    if (list.isEmpty()) { Muted("—" + if (goalText.isNotEmpty()) " · цель $goalText" else ""); return }
    val last = list.last(); val first = list.first()
    val dl = last.v - first.v
    val good = if (m.dir > 0) dl > 0 else dl < 0
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        Text(Js.fmt(last.v), style = MaterialTheme.typography.headlineSmall)
        Muted(m.unit)
        if (list.size > 1 && dl != 0.0) Badge((if (dl > 0) "+" else "") + Js.fmt(dl), if (good) DiaryTheme.extras.cardio else DiaryTheme.extras.danger)
        if (goalText.isNotEmpty()) Muted("цель $goalText")
    }
    Sparkline(list.takeLast(8).map { it.v }, goal = m.goal)
    if (m.goal != null) {
        val pct = if (m.dir > 0) (if (m.goal > 0) last.v / m.goal * 100 else 100.0) else (if (first.v > 0) (first.v - last.v) / first.v * 100 else 100.0)
        val p = Js.round(Js.clamp(pct, 0.0, 100.0)).toInt()
        MeterBar(p / 100f, "До цели: $p%")
    }
}

// ---------- журнал недели ----------

@Composable
private fun LogTab(vm: TrainingViewModel, data: TrainingData, today: String, integration: TrainingIntegration) {
    val ws0 = Dates.weekStart(today)
    val cur = data.wlog[ws0]
    val pw = Logic.planWeek(data.plan, ws0)
    DiaryCard {
        Text("Эта неделя" + if (Logic.inPlan(data.plan, pw)) " · неделя плана $pw" else "", style = MaterialTheme.typography.titleSmall)
        DebouncedField("Вес тела, кг", cur?.kg?.takeIf { it > 0 }?.let { Js.fmt(it) } ?: "", { vm.setWeek(ws0, kgText = it) }, decimal = true, placeholder = "Например, 65")
        DebouncedField("Заметка", cur?.note ?: "", { vm.setWeek(ws0, note = it) }, maxLength = 300)
        Muted("Взвешивайтесь раз в неделю, утром натощак. Занятия считаются сами по записям.")
    }
    val keys = data.wlog.filter { it.value.kg > 0 }.keys.sorted()
    if (keys.isNotEmpty()) {
        SectionTitle("Вес по неделям")
        val shown = keys.takeLast(12)
        LineChart(shown.map { ChartPoint(Dates.ddmm(it), data.wlog.getValue(it).kg) }, "кг", "Вес тела по неделям")
        val kcal = integration.weeklyKcal(shown)
        if (kcal != null && kcal.isNotEmpty()) {
            Text("Среднее ккал в день, съедено", style = MaterialTheme.typography.titleSmall)
            val pts = shown.mapNotNull { k -> kcal[k]?.let { ChartPoint(Dates.ddmm(k), it.toDouble()) } }
            if (pts.isNotEmpty()) LineChart(pts, "ккал", "Среднее ккал в день по неделям", color = DiaryTheme.extras.protein)
        }
        val first = keys.first()
        val months = maxOf(0.0, Dates.daysBetween(first, today) / 30.4)
        Muted(
            "Первый вес: ${Js.fmt(data.wlog.getValue(first).kg)} кг (${Dates.ddmm(first)})." +
                if (data.plan.protein) " Ориентир набора +0,5 кг в месяц: сейчас около ${Js.fmt(data.wlog.getValue(first).kg + 0.5 * months)} кг." else "",
        )
    }
    SectionTitle("Недели")
    DiaryCard {
        for (i in 0 until 10) {
            val ws = Dates.addDays(ws0, -7 * i)
            val c = Logic.weekCounts(data.sessions, ws)
            val e = data.wlog[ws]
            val w = Logic.planWeek(data.plan, ws)
            val bits = mutableListOf((if (e != null && e.kg > 0) Js.fmt(e.kg) + " кг" else "— кг"), "зал ${c.gym}/$WEEKLY_GOAL")
            data.plan.tracks.forEach { bits.add(it.name.substringBefore(' ').lowercase() + " " + (c.t[it.id] ?: 0)) }
            Row(Modifier.fillMaxWidth().padding0(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Dates.ddmm(ws) + if (Logic.inPlan(data.plan, w)) " · нед. $w" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                Column(Modifier.weight(0.6f)) {
                    Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    if (!e?.note.isNullOrEmpty()) Muted(e!!.note)
                }
            }
        }
    }
}
