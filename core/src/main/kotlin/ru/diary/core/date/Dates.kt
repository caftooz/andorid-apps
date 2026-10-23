package ru.diary.core.date

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Даты в виде строк `yyyy-MM-dd`, как в прототипе и в JSON-обмене. Недели начинаются с понедельника.
 */
object Dates {
    val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")

    fun isDate(s: String?): Boolean = s != null && DATE_RE.matches(s)

    /**
     * Разбор даты с «переполнением» полей, как у `new Date(y, m-1, d)` в JS:
     * `2026-02-31` превращается в 3 марта, а не вызывает ошибку.
     */
    fun parse(s: String): LocalDate {
        val a = s.split('-')
        return LocalDate.of(a[0].toInt(), 1, 1).plusMonths(a[1].toLong() - 1).plusDays(a[2].toLong() - 1)
    }

    fun format(d: LocalDate): String = "%d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)

    fun today(): String = format(LocalDate.now())

    fun addDays(s: String, n: Int): String = format(parse(s).plusDays(n.toLong()))

    /** Понедельник недели, в которую попадает дата. */
    fun weekStart(s: String): String {
        val d = parse(s)
        return format(d.minusDays(((d.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong()))
    }

    /** Индекс дня недели: 0 — понедельник, 6 — воскресенье. */
    fun dayIndex(s: String): Int = parse(s).dayOfWeek.value - 1

    fun daysBetween(from: String, to: String): Int = ChronoUnit.DAYS.between(parse(from), parse(to)).toInt()

    /** `dd.MM` для подписей на графиках. */
    fun ddmm(s: String): String = parse(s).let { "%02d.%02d".format(it.dayOfMonth, it.monthValue) }

    private val MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val WD = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    /** «18 мар», как `shortDate` прототипа. */
    fun shortDate(s: String): String = parse(s).let { "${it.dayOfMonth} ${MONTHS[it.monthValue - 1]}" }

    /** «Сегодня», «Вчера» или «18 мар, ср». */
    fun label(s: String, today: String = today()): String {
        if (s == today) return "Сегодня"
        if (s == addDays(today, -1)) return "Вчера"
        val d = parse(s)
        return "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}, ${WD[d.dayOfWeek.value - 1]}"
    }
}
