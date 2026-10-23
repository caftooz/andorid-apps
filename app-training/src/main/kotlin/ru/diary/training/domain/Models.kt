package ru.diary.training.domain

import kotlinx.serialization.Serializable

/** Подход: вес, повторы (или секунды), обратная связь `easy | ok | hard`. */
data class SetResult(val w: Double, val r: Int, val fb: String? = null)

/** Упражнение внутри сохранённой тренировки. `name` и `kind` могут отсутствовать в старых данных. */
data class ExResult(val id: String, val name: String?, val kind: String?, val sets: List<SetResult>)

/**
 * Запись о занятии. `type`: gym | volley | friends | other | track.
 * Для `track` в `tid` лежит id домашнего занятия, а `sl` — повторы по подходам за день.
 */
data class Session(
    val id: String,
    val date: String,
    val type: String,
    val prog: String?,
    val title: String,
    val start: Long,
    val min: Int,
    val rpe: Int,
    val pain: List<String>,
    val note: String,
    val ex: List<ExResult>,
    val tid: String? = null,
    val kcal: Int? = null,
    val sl: List<Int>? = null,
)

data class ProgramItem(val id: String, val sets: Int, val lo: Int, val hi: Int)

/** Программа зала. `rot` — участвует в автоматической очереди A → B → C. */
data class Program(
    val id: String,
    val title: String,
    val rot: Boolean,
    val warm: List<String>,
    val items: List<ProgramItem>,
)

/** Свое упражнение. `kind`: w (вес и повторы), bw (повторы), time (секунды). */
data class CustomEx(val id: String, val name: String, val kind: String, val step: Double, val tip: String)

/** Упражнение из каталога (встроенное или своё) в том виде, как его видит остальной код. */
data class ExDef(
    val id: String,
    val name: String,
    val kind: String,
    val step: Double,
    val sets: Int,
    val lo: Int,
    val hi: Int,
    val tip: String,
    val rest: Int = 0,
    val per: String = "",
    val custom: Boolean = false,
)

data class Block(val id: String, val name: String, val weeks: Int, val text: String)

/** День недели плана. `k`: gym | home | vb | rest. */
data class DayPlan(val k: String, val prog: String, val note: String)

data class Rule(val t: String, val d: String)

/** Показатель для замеров. `dir`: 1 — больше лучше, -1 — меньше лучше. */
data class Metric(
    val id: String,
    val name: String,
    val unit: String,
    val goal: Double?,
    val goalText: String,
    val dir: Int,
    val how: String,
)

data class TrackStep(val n: String, val t: String)
data class TrackItem(val n: String, val d: String, val t: String)

/** Домашнее занятие. `kind`: timed (по времени) или sets (подходы за день). */
data class Track(
    val id: String,
    val name: String,
    val kind: String,
    val ic: String,
    val goal: Int,
    val goalText: String,
    val dur: Int,
    val intro: String,
    val steps: List<TrackStep>,
    val cur: Int,
    val items: List<TrackItem>,
    val metric: String,
    val pctLo: Int,
    val pctHi: Int,
    val dayTarget: Int,
    val base: Int,
    val tip: String,
)

data class Plan(
    val name: String,
    val start: String,
    val protein: Boolean,
    val volleyHints: Boolean,
    val deloadEvery: Int,
    val testEvery: Int,
    val blocks: List<Block>,
    val days: List<DayPlan>,
    val rules: List<Rule>,
    val metrics: List<Metric>,
    val tracks: List<Track>,
)

/** Замер максимума. */
data class MaxEntry(val id: String, val date: String, val m: String, val v: Double)

/** Запись журнала недели: вес тела и заметка. */
data class WeekEntry(val kg: Double, val note: String)

data class Template(val id: String, val name: String, val plan: Plan, val programs: List<Program>)

/** Всё сохраняемое состояние дневника тренировок (то, что экспортируется в JSON). */
data class TrainingData(
    val sessions: List<Session> = emptyList(),
    val programs: List<Program> = emptyList(),
    val customEx: List<CustomEx> = emptyList(),
    val plan: Plan,
    val maxes: List<MaxEntry> = emptyList(),
    val wlog: Map<String, WeekEntry> = emptyMap(),
    val templates: List<Template> = emptyList(),
)

/** Подход в активной тренировке. */
@Serializable
data class ActiveSet(
    val w: Double,
    val r: Int,
    val done: Boolean = false,
    val fb: String? = null,
    val touched: Boolean = false,
)

/** Снимок описания упражнения на момент начала тренировки. */
@Serializable
data class ExSnap(val name: String, val kind: String, val step: Double, val tip: String, val rest: Int, val per: String)

@Serializable
data class ActiveEx(
    val id: String,
    val d: ExSnap,
    val tg: Int,
    val lo: Int,
    val hi: Int,
    val hint: String,
    val first: Boolean,
    val sets: List<ActiveSet>,
)

/** Идущая тренировка. Переживает закрытие приложения: хранится в Room после каждого действия. */
@Serializable
data class ActiveWorkout(
    val id: String,
    val prog: String?,
    val title: String,
    val date: String,
    val start: Long,
    val warm: Map<Int, Boolean>,
    val warmList: List<String>,
    val ex: List<ActiveEx>,
    /** Момент окончания отдыха (мс), 0 — отдых не идёт. Хранится, чтобы таймер пережил перезапуск процесса. */
    val restEnd: Long = 0L,
    val restLabel: String = "",
)

/** Совет по весу и повторам для следующего подхода. */
data class Suggestion(val w: Double, val r: Int, val hint: String, val first: Boolean = false)

data class WeekCounts(
    val gym: Int,
    val volley: Int,
    val friends: Int,
    val other: Int,
    val total: Int,
    val t: Map<String, Int>,
)

data class WeekTotals(val min: Int, val kcal: Int)

data class MaxStatus(val testWeek: Boolean, val cnt: Int, val days: Int?, val due: Boolean, val text: String)
