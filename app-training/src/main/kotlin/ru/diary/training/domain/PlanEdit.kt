package ru.diary.training.domain

import ru.diary.core.util.Js

/** Правки плана, перенесённые из обработчиков редактора прототипа. Все функции чистые. */
object PlanEdit {
    private fun <T> List<T>.set(i: Int, v: T): List<T> = toMutableList().also { if (i in it.indices) it[i] = v }

    // ---------- дни и правила ----------

    fun setDayKind(p: Plan, i: Int, k: String): Plan = p.copy(days = p.days.set(i, p.days[i].copy(k = k)))
    fun setDayProg(p: Plan, i: Int, prog: String): Plan = p.copy(days = p.days.set(i, p.days[i].copy(prog = prog)))
    fun setDayNote(p: Plan, i: Int, note: String): Plan = p.copy(days = p.days.set(i, p.days[i].copy(note = note.take(120))))

    /** Разбор строк «Заголовок | текст» в правила (до 20 строк, поля обрезаются). */
    fun rulesFromText(text: String): List<Rule> =
        linesOf(text, 2).map { Rule(it[0].take(60), it[1].take(200)) }

    fun rulesToText(rules: List<Rule>): String = joinLines(rules.map { listOf(it.t, it.d) })

    // ---------- блоки ----------

    fun addBlock(p: Plan): Plan = p.copy(blocks = p.blocks + Block("b-" + Ids.uid(), "Новый блок", 4, ""))

    fun removeBlock(p: Plan, i: Int): Plan = if (p.blocks.size > 1) p.copy(blocks = p.blocks.filterIndexed { k, _ -> k != i }) else p

    fun moveBlock(p: Plan, i: Int, dir: Int): Plan {
        val t = i + dir
        if (t < 0 || t >= p.blocks.size || i !in p.blocks.indices) return p
        val l = p.blocks.toMutableList()
        l[i] = p.blocks[t]; l[t] = p.blocks[i]
        return p.copy(blocks = l)
    }

    fun stepBlockWeeks(p: Plan, i: Int, dir: Int): Plan =
        p.blocks.getOrNull(i)?.let { b -> p.copy(blocks = p.blocks.set(i, b.copy(weeks = Js.clamp(b.weeks + dir, 1, 104)))) } ?: p

    fun editBlock(p: Plan, i: Int, name: String? = null, text: String? = null): Plan =
        p.blocks.getOrNull(i)?.let { b -> p.copy(blocks = p.blocks.set(i, b.copy(name = name?.take(40) ?: b.name, text = text?.take(300) ?: b.text))) } ?: p

    fun stepDeload(p: Plan, dir: Int): Plan = p.copy(deloadEvery = Js.clamp(p.deloadEvery + dir, 0, 20))
    fun stepTest(p: Plan, dir: Int): Plan = p.copy(testEvery = Js.clamp(p.testEvery + dir, 0, 20))

    // ---------- замеры ----------

    fun newMetric(): Metric = Metric("m-" + Ids.uid(), "Новый показатель", "раз", null, "", 1, "")

    fun editMetric(
        p: Plan, id: String, name: String? = null, unit: String? = null, goalText: String? = null, how: String? = null,
        goal: String? = null, dir: Int? = null,
    ): Plan = p.copy(metrics = p.metrics.map { m ->
        if (m.id != id) m else m.copy(
            name = name?.take(60) ?: m.name, unit = unit?.take(20) ?: m.unit, how = how?.take(200) ?: m.how, goalText = goalText?.take(80) ?: m.goalText,
            goal = if (goal != null) (if (goal.trim().isEmpty()) null else Js.parseNum(goal)) else m.goal,
            dir = dir?.let { if (it < 0) -1 else 1 } ?: m.dir,
        )
    })

    // ---------- домашние занятия ----------

    fun newTrack(kind: String): Track = Track(
        id = "t-" + Ids.uid(), name = if (kind == "sets") "Подходы за день" else "Новое занятие", kind = if (kind == "sets") "sets" else "timed",
        ic = if (kind == "sets") "gym" else "star", goal = 0, goalText = "", dur = 20, intro = "", steps = emptyList(), cur = 0, items = emptyList(),
        metric = "", pctLo = 40, pctHi = 50, dayTarget = 8, base = 10, tip = "",
    )

    enum class TrackField { Goal, Dur, PctLo, PctHi, DayTarget }

    fun stepTrack(t: Track, f: TrackField, dir: Int): Track = when (f) {
        TrackField.Goal -> t.copy(goal = Js.clamp(t.goal + dir, 0, 7))
        TrackField.Dur -> t.copy(dur = Js.clamp(t.dur + dir * 5, 5, 600))
        TrackField.PctLo -> Js.clamp(t.pctLo + dir * 5, 5, 100).let { lo -> t.copy(pctLo = lo, pctHi = maxOf(t.pctHi, lo)) }
        TrackField.PctHi -> Js.clamp(t.pctHi + dir * 5, 5, 100).let { hi -> t.copy(pctHi = hi, pctLo = minOf(t.pctLo, hi)) }
        TrackField.DayTarget -> t.copy(dayTarget = Js.clamp(t.dayTarget + dir, 1, 30))
    }

    /** Второе упражнение связки (пусто — без связки); его база — последний замер этого показателя. */
    fun setTrackPair(t: Track, metricId: String, plan: Plan, maxes: List<MaxEntry>): Track {
        if (metricId.isEmpty() || plan.metrics.none { it.id == metricId }) return t.copy(pair = "")
        val last = Logic.maxesOf(maxes, metricId).lastOrNull()
        return t.copy(pair = metricId, base2 = last?.let { Js.clamp(Js.round(it.v), 1.0, 999.0).toInt() } ?: t.base2)
    }

    /** При выборе показателя-максимума берётся последний замер как база подходов. */
    fun setTrackMetric(t: Track, metricId: String, plan: Plan, maxes: List<MaxEntry>): Track {
        val tm = t.copy(metric = metricId)
        if (plan.metrics.none { it.id == metricId }) return tm
        val last = Logic.maxesOf(maxes, metricId).lastOrNull() ?: return tm
        return tm.copy(base = Js.clamp(Js.round(last.v), 1.0, 999.0).toInt())
    }

    fun stepsFromText(text: String): List<TrackStep> = linesOf(text, 2).take(12).map { TrackStep(it[0].take(80), it[1].take(300)) }
    fun stepsToText(l: List<TrackStep>): String = joinLines(l.map { listOf(it.n, it.t) })
    fun itemsFromText(text: String): List<TrackItem> = linesOf(text, 3).map { TrackItem(it[0].take(80), it[1].take(80), it[2].take(200)) }
    fun itemsToText(l: List<TrackItem>): String = joinLines(l.map { listOf(it.n, it.d, it.t) })

    fun setTrack(p: Plan, id: String, f: (Track) -> Track): Plan = p.copy(tracks = p.tracks.map { if (it.id == id) f(it) else it })

    // ---------- строки «a | b | c» ----------

    /** `linesOf`: непустые строки, поля через «|», до 20 строк; недостающие поля пустые. */
    fun linesOf(text: String, parts: Int): List<List<String>> =
        text.split('\n').map { l -> l.split('|').map { it.trim() } }.filter { it[0].isNotEmpty() }.take(20)
            .map { a -> List(parts) { i -> a.getOrElse(i) { "" } } }

    /** `joinLines`: хвостовые пустые поля отбрасываются, но первое остаётся. */
    fun joinLines(rows: List<List<String>>): String = rows.joinToString("\n") { r ->
        val a = r.toMutableList()
        while (a.size > 1 && a.last().isEmpty()) a.removeAt(a.size - 1)
        a.joinToString(" | ")
    }
}
