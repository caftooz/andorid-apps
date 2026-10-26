package ru.diary.training.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.util.Js
import ru.diary.training.domain.Cleaning
import ru.diary.training.domain.DayPlan
import ru.diary.training.domain.Plan
import ru.diary.training.domain.Program
import ru.diary.training.domain.Session
import ru.diary.training.domain.Track

/** Тексты и подписи, перенесённые из прототипа. */
object Texts {
    val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val DAY_KINDS = linkedMapOf("gym" to "Зал", "home" to "Дом", "vb" to "Волейбол", "rest" to "Отдых")
    val INT_LABEL = listOf("", "Лёгкая", "Спокойная", "Средняя", "Тяжёлая", "Очень тяжёлая")
    val RPE_LABEL = listOf("", "Очень легко", "Легко", "Нормально", "Тяжело", "На пределе")
    val PAIN = listOf("Плечо", "Колено", "Спина", "Голеностоп", "Другое")
    val FB = linkedMapOf("easy" to "Легко", "ok" to "Нормально", "hard" to "Тяжело")
    val KIND_NAME = linkedMapOf("w" to "с весом", "bw" to "повторы без веса", "time" to "на время")
    val ACT_PRESETS = listOf("Велотренажёр", "Бег", "Ходьба", "Плавание", "Велосипед")
    val TRACK_KIND = linkedMapOf("timed" to "По времени", "sets" to "Подходы за день")
    val TRACK_DURS = listOf(10, 15, 20, 30, 45, 60)

    fun typeName(s: Session, plan: Plan): String = when (s.type) {
        "track" -> plan.tracks.firstOrNull { it.id == s.tid }?.name ?: "Занятие"
        "other" -> s.title.ifEmpty { "Кардио" }
        else -> Cleaning.TYPE_NAMES[s.type] ?: "Занятие"
    }

    fun quickName(q: String, plan: Plan): String =
        if (q.startsWith("t:")) plan.tracks.firstOrNull { it.id == q.substring(2) }?.name ?: "Занятие" else Cleaning.TYPE_NAMES[q] ?: q

    fun quickDurations(q: String): List<Int> = if (q.startsWith("t:")) TRACK_DURS else if (q == "other") listOf(15, 20, 30, 45, 60) else listOf(30, 45, 60, 90, 120)

    fun quickDefault(q: String, plan: Plan): Int =
        if (q.startsWith("t:")) plan.tracks.firstOrNull { it.id == q.substring(2) }?.dur?.takeIf { it > 0 } ?: 20
        else if (q == "volley") 90 else if (q == "other") 30 else 60

    fun sessionTitle(s: Session): String = s.title.ifEmpty { if (s.prog != null) "Программа ${s.prog}" else "Зал" }

    fun sessionSummary(s: Session, plan: Plan): String {
        val kcal = s.kcal?.let { " · $it ккал" } ?: ""
        if (s.type == "gym") {
            val sets = s.ex.sumOf { it.sets.size }
            return "${sessionTitle(s)} · ${s.min} мин · $sets ${Js.plural(sets, "подход", "подхода", "подходов")}$kcal"
        }
        val sl = s.sl
        if (s.type == "track" && !sl.isNullOrEmpty()) {
            val sets = "${typeName(s, plan)} · ${sl.size} ${Js.plural(sl.size, "подход", "подхода", "подходов")}"
            val sl2 = s.sl2
            val tr = plan.tracks.firstOrNull { it.id == s.tid }
            if (!sl2.isNullOrEmpty() && tr != null) {
                return "$sets · ${exName(tr.metric, tr, plan).lowercase()} ${sl.sum()} + ${exName(tr.pair, tr, plan).lowercase()} ${sl2.sum()}"
            }
            return "$sets · ${sl.sum()} повт."
        }
        return "${typeName(s, plan)} · ${s.min} мин$kcal" + if (s.rpe > 0) " · нагрузка ${s.rpe} из 5" else ""
    }

    /** Название упражнения «подходов за день»: показатель или, если его нет, само занятие. */
    fun exName(metricId: String, t: Track, plan: Plan): String =
        plan.metrics.firstOrNull { it.id == metricId }?.name ?: if (metricId.isEmpty()) t.name else "Упражнение"

    /** «Подтягивания 5 + отжимания 10» — что добавит одна кнопка связки. */
    fun pairLine(t: Track, plan: Plan, hi: Int, hi2: Int): String =
        "${exName(t.metric, t, plan)} $hi + ${exName(t.pair, t, plan).lowercase()} $hi2"

    fun dayLabel(d: DayPlan, programs: List<Program>): String {
        val p = if (d.k == "gym" && d.prog.isNotEmpty()) programs.firstOrNull { it.id == d.prog } else null
        return (DAY_KINDS[d.k] ?: "") + (p?.let { " · ${it.title}" } ?: "")
    }

    /** Категория занятия для цвета и иконки: gym, volley, friends, cardio, home. */
    fun cat(s: Session): String = when (s.type) {
        "gym" -> "gym"; "volley" -> "volley"; "friends" -> "friends"; "other" -> "cardio"; else -> "home"
    }

    val CAT_ICON = mapOf("gym" to "gym", "volley" to "vb", "friends" to "friends", "cardio" to "cardio", "home" to "star")
    val CAT_NAME = mapOf("gym" to "Зал", "volley" to "Волейбол", "friends" to "Друзья", "cardio" to "Кардио", "home" to "Дома")
    val CAT_ORDER = listOf("gym", "volley", "cardio", "friends", "home")

    @Composable
    fun catColor(cat: String): Color = when (cat) {
        "gym" -> MaterialTheme.colorScheme.primary
        "volley" -> DiaryTheme.extras.volley
        "friends" -> DiaryTheme.extras.friends
        "cardio" -> DiaryTheme.extras.cardio
        else -> DiaryTheme.extras.home
    }

    /** Иконка дня плана. */
    fun dayIcon(kind: String): String = when (kind) { "gym" -> "gym"; "vb" -> "vb"; "home" -> "home"; else -> "rest" }

    fun dateLabel(date: String, today: String): String = Dates.label(date, today)
}
