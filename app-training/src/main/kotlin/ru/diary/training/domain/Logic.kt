package ru.diary.training.domain

import ru.diary.core.date.Dates
import ru.diary.core.util.Js
import kotlin.math.ceil

/** Цель зала: занятий в неделю (`GOAL` прототипа). */
const val WEEKLY_GOAL = 3

/**
 * Чистая бизнес-логика дневника тренировок. Все функции принимают состояние и «сегодня» явно,
 * чтобы их можно было проверять тестами без часов и без Android.
 */
object Logic {

    // ---------- прогрессия ----------

    private class Last(val key: String, val e: ExResult)

    private fun sortKey(s: Session): String = s.date + s.start.toString()

    /** Последняя тренировка в зале, где делалось упражнение (`lastWith`). */
    private fun lastWith(sessions: List<Session>, exId: String): Last? {
        var best: Last? = null
        for (s in sessions) {
            if (s.type != "gym") continue
            for (e in s.ex) {
                if (e.id == exId && e.sets.isNotEmpty()) {
                    val key = sortKey(s)
                    if (best == null || key > best.key) best = Last(key, e)
                }
            }
        }
        return best
    }

    /**
     * Двойная прогрессия и автоподбор веса (`suggest`).
     * Для весовых упражнений: «легко» или все подходы на верхней границе без «тяжело» — вес растёт на шаг;
     * «тяжело» в последнем подходе — вес падает на шаг. Для остальных — только подсказка.
     */
    fun suggest(catalog: ExerciseCatalog, sessions: List<Session>, exId: String, tg: Int, lo: Int, hi: Int): Suggestion {
        val d = catalog.defOf(exId)
        val kind = d?.kind ?: "w"
        val step = d?.step ?: 1.0
        val b = lastWith(sessions, exId)
        if (b == null) {
            return Suggestion(0.0, lo, if (kind == "w") "Первый раз: начните с лёгкого веса" else "", first = true)
        }
        val sets = b.e.sets
        val last = sets.last()
        var w = last.w
        var r = if (last.r == 0) lo else Js.clamp(last.r, 1, 999)
        var hint = ""
        if (kind == "w") {
            val allTop = sets.size >= tg && sets.all { it.r >= hi }
            val anyHard = sets.any { it.fb == "hard" }
            if (last.fb == "easy" || (allTop && !anyHard)) {
                w += step
                r = lo
                hint = "Прошлый раз: ${Js.fmt(last.w)} кг × ${last.r}. Пробуйте ${Js.fmt(w)} кг"
            } else if (last.fb == "hard") {
                w = maxOf(0.0, w - step)
                r = lo
                hint = "Прошлый раз было тяжело при ${Js.fmt(last.w)} кг. Начните с ${Js.fmt(w)} кг"
            } else {
                hint = "Прошлый раз: ${Js.fmt(last.w)} кг × ${last.r}"
            }
        } else {
            val tm = kind == "time"
            val allTop = sets.size >= tg && sets.all { it.r >= hi }
            hint = "Прошлый раз: ${last.r}" + (if (tm) " с" else " повт.")
            if (allTop) {
                r = lo
                hint += ". Верхняя граница во всех подходах: " +
                    (if (tm) "добавьте секунд или усложните вариант" else "усложните вариант (медленнее, с паузой, ноги выше) или добавьте вес") +
                    ", начните с $lo"
            }
        }
        return Suggestion(w, r, hint)
    }

    /** Следующая программа из очереди (`nextProg`). */
    fun nextProg(programs: List<Program>, sessions: List<Session>): String? {
        val rot = programs.filter { it.rot }
        if (rot.isEmpty()) return programs.firstOrNull()?.id
        val ids = rot.map { it.id }
        var best: Pair<String, String>? = null
        for (s in sessions) {
            if (s.type == "gym" && s.prog != null && s.prog in ids) {
                val key = sortKey(s)
                if (best == null || key > best.first) best = key to s.prog
            }
        }
        if (best == null) return ids[0]
        return ids[(ids.indexOf(best.second) + 1) % ids.size]
    }

    // ---------- недели и серия ----------

    fun weekCounts(sessions: List<Session>, ws: String): WeekCounts {
        val we = Dates.addDays(ws, 6)
        var gym = 0; var volley = 0; var friends = 0; var other = 0; var total = 0
        val t = LinkedHashMap<String, Int>()
        val seen = HashSet<String>()
        for (s in sessions) {
            if (s.date < ws || s.date > we) continue
            if (s.type == "track") {
                val tid = s.tid ?: "undefined"
                if (seen.add(tid + s.date)) t[tid] = (t[tid] ?: 0) + 1
            } else {
                when (s.type) {
                    "gym" -> { gym++; total++ }
                    "volley" -> { volley++; total++ }
                    "friends" -> { friends++; total++ }
                    "other" -> { other++; total++ }
                }
            }
        }
        return WeekCounts(gym, volley, friends, other, total, t)
    }

    /** Сколько недель подряд выполнена цель (`streak`): текущая неделя засчитывается, если цель уже достигнута. */
    fun streak(sessions: List<Session>, today: String): Int {
        var w = Dates.weekStart(today)
        var n = 0
        if (weekCounts(sessions, w).total >= WEEKLY_GOAL) n++
        w = Dates.addDays(w, -7)
        for (i in 0 until 520) {
            if (weekCounts(sessions, w).total >= WEEKLY_GOAL) {
                n++
                w = Dates.addDays(w, -7)
            } else break
        }
        return n
    }

    fun weekTotals(sessions: List<Session>, ws: String): WeekTotals {
        val we = Dates.addDays(ws, 6)
        var min = 0; var kcal = 0
        for (s in sessions) if (s.date >= ws && s.date <= we) {
            min += s.min
            kcal += s.kcal ?: 0
        }
        return WeekTotals(min, kcal)
    }

    // ---------- план ----------

    fun planTotal(plan: Plan): Int = maxOf(1, plan.blocks.sumOf { it.weeks })

    /** Номер недели плана для даты или `null`, если старт не задан. До старта номер ≤ 0. */
    fun planWeek(plan: Plan, date: String): Int? {
        if (plan.start.isEmpty()) return null
        val days = Dates.daysBetween(Dates.weekStart(plan.start), date)
        return Math.floorDiv(days, 7) + 1
    }

    fun blockOf(plan: Plan, week: Int): Block {
        var acc = 0
        for (b in plan.blocks) {
            acc += b.weeks
            if (week <= acc) return b
        }
        return plan.blocks.last()
    }

    fun isDeload(plan: Plan, w: Int): Boolean {
        val e = plan.deloadEvery
        return e > 0 && w >= 1 && w <= planTotal(plan) && w % e == 0
    }

    fun isTest(plan: Plan, w: Int): Boolean {
        val e = plan.testEvery
        return e > 0 && w >= 1 && w <= planTotal(plan) && w % e == 0
    }

    fun inPlan(plan: Plan, w: Int?): Boolean = w != null && w >= 1 && w <= planTotal(plan)

    fun deloadNow(plan: Plan, today: String): Boolean {
        val w = planWeek(plan, today) ?: return false
        return isDeload(plan, w)
    }

    fun planDay(plan: Plan, today: String): DayPlan = plan.days[Dates.dayIndex(today)]

    /** Программа, назначенная на сегодня планом (если такая программа существует). */
    fun planDayProg(plan: Plan, programs: List<Program>, today: String): String? {
        val d = planDay(plan, today)
        return if (d.k == "gym" && d.prog.isNotEmpty() && programs.any { it.id == d.prog }) d.prog else null
    }

    fun todayProg(plan: Plan, programs: List<Program>, sessions: List<Session>, today: String): String? =
        planDayProg(plan, programs, today) ?: nextProg(programs, sessions)

    // ---------- замеры ----------

    fun maxesOf(maxes: List<MaxEntry>, m: String): List<MaxEntry> =
        maxes.filter { it.m == m }.sortedWith { a, b -> a.date.compareTo(b.date) }

    fun bestOf(plan: Plan, maxes: List<MaxEntry>, m: String): Double? {
        val metric = plan.metrics.firstOrNull { it.id == m } ?: return null
        var best: Double? = null
        for (x in maxesOf(maxes, m)) {
            val b = best
            if (b == null || (if (metric.dir > 0) x.v > b else x.v < b)) best = x.v
        }
        return best
    }

    /** Состояние замеров на этой неделе (`maxStatus`): неделя тестов, давность, нужен ли замер. */
    fun maxStatus(plan: Plan, maxes: List<MaxEntry>, today: String): MaxStatus {
        val w = planWeek(plan, today)
        val ws = Dates.weekStart(today)
        val we = Dates.addDays(ws, 6)
        val done = HashSet<String>()
        var last = ""
        val n = plan.metrics.size
        for (x in maxes) {
            if (plan.metrics.none { it.id == x.m }) continue
            if (x.date >= ws && x.date <= we) done.add(x.m)
            if (x.date > last) last = x.date
        }
        val cnt = done.size
        val days = if (last.isNotEmpty()) Dates.daysBetween(last, today) else null
        val testWeek = w != null && isTest(plan, w)
        val text = when {
            testWeek -> "Неделя тестов: замерьте максимумы. Силовые показатели лучше в разные дни. Сделано $cnt из $n за эту неделю."
            days == null -> "Замеров пока нет. Сделайте стартовые, чтобы видеть прогресс."
            days >= 28 -> "Последний замер был $days ${Js.plural(days, "день", "дня", "дней")} назад: пора обновить."
            else -> "Последний замер был " + (if (days == 0) "сегодня" else "$days ${Js.plural(days, "день", "дня", "дней")} назад") + "."
        }
        return MaxStatus(testWeek, cnt, days, n > 0 && (testWeek || days == null || days >= 28), text)
    }

    /** Подсказка после волейбола (`volleyNote`). */
    fun volleyNote(plan: Plan, sessions: List<Session>, today: String): String {
        if (!plan.volleyHints) return ""
        val y = Dates.addDays(today, -1)
        fun has(d: String) = sessions.any { it.date == d && it.type == "volley" }
        return when {
            has(today) -> "Сегодня игра: без прыжков в зале."
            has(y) -> "Вчера был волейбол: ноги могут быть «забиты»."
            else -> ""
        }
    }

    /** Последний записанный вес тела; 65 кг, если записей нет (`latestWeight`). */
    fun latestWeight(wlog: Map<String, WeekEntry>): Double {
        val keys = wlog.filter { it.value.kg > 0 }.keys.sorted()
        return if (keys.isEmpty()) 65.0 else wlog.getValue(keys.last()).kg
    }

    // ---------- домашние занятия ----------

    /** Диапазон повторов в подходе для занятия «подходы за день» (`setReps`). */
    fun setReps(base: Int, pctLo: Int, pctHi: Int): Pair<Int, Int> {
        val lo = maxOf(1.0, Js.round(base * pctLo / 100.0)).toInt()
        val hi = maxOf(lo.toDouble(), Js.round(base * pctHi / 100.0)).toInt()
        return lo to hi
    }

    fun setsToday(sessions: List<Session>, tid: String, today: String): List<Int> {
        var r: List<Int> = emptyList()
        for (x in sessions) if (x.type == "track" && x.tid == tid && x.date == today) r = x.sl ?: emptyList()
        return r
    }

    /** Повторы второго упражнения связки за день. */
    fun pairSetsToday(sessions: List<Session>, tid: String, today: String): List<Int> {
        var r: List<Int> = emptyList()
        for (x in sessions) if (x.type == "track" && x.tid == tid && x.date == today) r = x.sl2 ?: emptyList()
        return r
    }

    /** Повторы для кнопки «+ подход»: верх диапазона основного упражнения и, если есть связка, второго. */
    fun trackReps(t: Track, plan: Plan): Pair<Int, Int?> {
        val hi = setReps(t.base, t.pctLo, t.pctHi).second
        val hi2 = if (t.pair.isNotEmpty() && plan.metrics.any { it.id == t.pair }) setReps(t.base2, t.pctLo, t.pctHi).second else null
        return hi to hi2
    }

    // ---------- программы ----------

    /** Краткое описание программы: число упражнений, подходов и оценка времени (`progBrief`). */
    fun progBrief(p: Program): String {
        val sets = p.items.sumOf { it.sets }
        val n = p.items.size
        val minutes = (Js.round((sets * 2.5 + 10) / 5) * 5).toInt()
        return "$n ${Js.plural(n, "упражнение", "упражнения", "упражнений")} · $sets подходов · ~$minutes мин"
    }

    // ---------- активная тренировка ----------

    fun buildActiveEx(
        catalog: ExerciseCatalog, sessions: List<Session>, plan: Plan, today: String, item: ProgramItem,
    ): ActiveEx {
        val d = catalog.defOf(item.id) ?: ExDef(item.id, item.id, "w", 1.0, 3, 8, 10, "")
        val sg = suggest(catalog, sessions, item.id, item.sets, item.lo, item.hi)
        val n = if (deloadNow(plan, today)) maxOf(1, ceil(item.sets / 2.0).toInt()) else item.sets
        return ActiveEx(
            id = item.id, d = catalog.snapOf(d), tg = n, lo = item.lo, hi = item.hi, hint = sg.hint, first = sg.first,
            sets = List(n) { ActiveSet(sg.w, sg.r) },
        )
    }

    fun startWorkout(data: TrainingData, progId: String?, today: String, now: Long): ActiveWorkout {
        val catalog = ExerciseCatalog(data.customEx)
        val p = progId?.let { id -> data.programs.firstOrNull { it.id == id } }
        return ActiveWorkout(
            id = Ids.uid(now), prog = p?.id, title = p?.title ?: "Своя тренировка", date = today, start = now,
            warm = emptyMap(), warmList = p?.warm ?: BuiltinData.warmGen,
            ex = p?.items?.map { buildActiveEx(catalog, data.sessions, data.plan, today, it) } ?: emptyList(),
        )
    }

    private fun round1(x: Double): Double = Js.round(x * 10) / 10

    /** Кнопки − и + у веса или повторов. Выполненный подход не меняется. */
    fun stepSet(a: ActiveWorkout, i: Int, j: Int, field: Char, dir: Int): ActiveWorkout {
        val ex = a.ex[i]
        val s = ex.sets[j]
        if (s.done) return a
        val amount = if (field == 'w') ex.d.step else if (ex.d.kind == "time") 5.0 else 1.0
        val ns = if (field == 'w') s.copy(w = maxOf(0.0, round1(s.w + dir * amount)), touched = true)
        else s.copy(r = maxOf(0, s.r + (dir * amount).toInt()), touched = true)
        return a.replaceSet(i, j, ns)
    }

    /** Ввод значения с клавиатуры. */
    fun inputSet(a: ActiveWorkout, i: Int, j: Int, field: Char, text: String): ActiveWorkout {
        val s = a.ex.getOrNull(i)?.sets?.getOrNull(j) ?: return a
        val v = Js.parseNum(text)
        val ns = if (field == 'w') s.copy(w = v, touched = true) else s.copy(r = Js.round(v).toInt(), touched = true)
        return a.replaceSet(i, j, ns)
    }

    private fun nextUndone(ex: ActiveEx, j: Int): Int? {
        for (q in j + 1 until ex.sets.size) if (!ex.sets[q].done) return q
        return null
    }

    /**
     * Отметка подхода. При выполнении следующий несделанный подход получает тот же вес,
     * если пользователь его не менял и упражнение весовое.
     */
    fun toggleDone(a: ActiveWorkout, i: Int, j: Int): ActiveWorkout {
        val ex = a.ex[i]
        val st = ex.sets[j]
        var sets = ex.sets.toMutableList()
        val done = !st.done
        sets[j] = st.copy(done = done)
        if (done) {
            val nx = nextUndone(ex, j)
            if (nx != null && !sets[nx].touched && ex.d.kind == "w") sets[nx] = sets[nx].copy(w = st.w)
        }
        return a.copy(ex = a.ex.toMutableList().also { it[i] = ex.copy(sets = sets) })
    }

    /** Обратная связь по подходу: следующий неизменённый подход получает вес ±шаг. */
    fun setFeedback(a: ActiveWorkout, i: Int, j: Int, fb: String): ActiveWorkout {
        val ex = a.ex[i]
        val st = ex.sets[j]
        val newFb = if (st.fb == fb) null else fb
        val sets = ex.sets.toMutableList()
        sets[j] = st.copy(fb = newFb)
        val nx = nextUndone(ex, j)
        if (nx != null && !sets[nx].touched) {
            val delta = when (newFb) { "easy" -> ex.d.step; "hard" -> -ex.d.step; else -> 0.0 }
            sets[nx] = sets[nx].copy(w = maxOf(0.0, round1(st.w + delta)))
        }
        return a.copy(ex = a.ex.toMutableList().also { it[i] = ex.copy(sets = sets) })
    }

    fun addSet(a: ActiveWorkout, i: Int): ActiveWorkout {
        val ex = a.ex[i]
        val last = ex.sets.last()
        return a.copy(ex = a.ex.toMutableList().also { it[i] = ex.copy(sets = ex.sets + ActiveSet(last.w, last.r)) })
    }

    fun addExercise(a: ActiveWorkout, ex: ActiveEx): ActiveWorkout = a.copy(ex = a.ex + ex)

    /** Секунды отдыха после подхода: авто-значение упражнения или выбранное вручную. */
    fun restSeconds(ex: ActiveEx, restAuto: Boolean, restLen: Int): Int = if (restAuto && ex.d.rest > 0) ex.d.rest else restLen

    /** Первое упражнение, где остались невыполненные подходы. */
    fun nextOpenEx(a: ActiveWorkout): String? = a.ex.firstOrNull { e -> e.sets.any { !it.done } }?.id

    fun doneCount(e: ActiveEx): Int = e.sets.count { it.done }

    /** Итоги формы завершения. */
    data class Finish(val rpe: Int = 3, val pain: List<String> = emptyList(), val note: String = "", val kcal: String = "")

    /** Сохранение тренировки в историю. Подходы без отметки «выполнено» не попадают в запись. */
    fun finishSession(a: ActiveWorkout, fin: Finish, now: Long): Session {
        val ex = a.ex.mapNotNull { e ->
            val done = e.sets.filter { it.done }.map { SetResult(it.w, it.r, it.fb) }
            if (done.isEmpty()) null else ExResult(e.id, e.d.name, e.d.kind, done)
        }
        val fk = Js.round(Js.parseNum(fin.kcal)).toInt()
        return Session(
            id = a.id, date = a.date, type = "gym", prog = a.prog, title = a.title, start = a.start,
            min = maxOf(1.0, Js.round((now - a.start) / 60000.0)).toInt(), rpe = fin.rpe, pain = fin.pain,
            note = fin.note.take(1000), ex = ex, kcal = if (fk > 0) fk else null,
        )
    }

    // ---------- быстрая запись ----------

    data class QuickForm(
        val day: String? = null, val dur: Int = 90, val int: Int = 3, val note: String = "", val name: String = "", val kcal: String = "",
    )

    /** Запись занятия без тренировки: кардио, волейбол, друзья, домашнее занятие `t:<id>`. Возвращает `null`, если не хватает названия. */
    fun quickSession(q: String, f: QuickForm, today: String, now: Long): Session? {
        val isTrack = q.startsWith("t:")
        if (q == "other" && f.name.trim().isEmpty()) return null
        val kc = Js.round(Js.parseNum(f.kcal)).toInt()
        return Session(
            id = Ids.uid(now), date = f.day ?: today, type = if (isTrack) "track" else q, prog = null,
            title = if (q == "other") f.name.trim().take(60) else "", start = now, min = f.dur,
            rpe = if (isTrack) 0 else f.int, pain = emptyList(), note = f.note.take(1000), ex = emptyList(),
            tid = if (isTrack) q.substring(2) else null, kcal = if (kc > 0) kc else null,
        )
    }

    /** Добавить подход к занятию «подходы за день»; создаёт запись дня при необходимости. */
    fun addTrackSet(sessions: List<Session>, tid: String, reps: Int, today: String, now: Long, reps2: Int? = null): List<Session> {
        val idx = sessions.indexOfLast { it.type == "track" && it.tid == tid && it.date == today }
        if (idx < 0) {
            val s = Session(
                Ids.uid(now), today, "track", null, "", now, 0, 0, emptyList(), "", emptyList(), tid = tid,
                sl = listOf(reps), sl2 = reps2?.let { listOf(it) },
            )
            return sessions + s
        }
        val cur = sessions[idx]
        val sl2 = if (reps2 != null) (cur.sl2 ?: emptyList()) + reps2 else cur.sl2
        return sessions.toMutableList().also { it[idx] = cur.copy(sl = (cur.sl ?: emptyList()) + reps, sl2 = sl2) }
    }

    /** Убрать последний подход; пустая запись дня удаляется. */
    fun undoTrackSet(sessions: List<Session>, tid: String, today: String): List<Session> {
        val idx = sessions.indexOfLast { it.type == "track" && it.tid == tid && it.date == today }
        if (idx < 0) return sessions
        val cur = sessions[idx]
        val sl = (cur.sl ?: emptyList()).dropLast(1)
        // Подходы связки идут парами: убираем и второе упражнение, если его последний подход был в этой паре.
        val sl2 = cur.sl2?.let { if (it.size > sl.size) it.dropLast(1) else it }?.takeIf { it.isNotEmpty() }
        return if (sl.isEmpty()) sessions.filterIndexed { i, _ -> i != idx }
        else sessions.toMutableList().also { it[idx] = cur.copy(sl = sl, sl2 = sl2) }
    }
}

private fun ActiveWorkout.replaceSet(i: Int, j: Int, s: ActiveSet): ActiveWorkout {
    val ex = this.ex[i]
    return copy(ex = this.ex.toMutableList().also { it[i] = ex.copy(sets = ex.sets.toMutableList().also { l -> l[j] = s }) })
}
