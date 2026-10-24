package ru.diary.training.ui

import ru.diary.training.domain.Program

/**
 * Буква программы. Хранится прямо в названии, как в прототипе: «A · ноги и жимы». Поэтому схема базы
 * и формат экспорта не меняются, а веб-версия видит то же название.
 */
object ProgramLetters {
    /** Варианты буквы в редакторе программы; пустая строка — без буквы. */
    val CHOICES = listOf("", "A", "B", "C", "D", "E", "F")

    private val prefix = Regex("""^\s*([A-Za-zА-Яа-яЁё0-9]{1,2})\s*[·.:–—-]\s*(.+)$""")

    /** Буква и название без неё. Если буквы в названии нет, буква пустая. */
    fun split(title: String): Pair<String, String> =
        prefix.matchEntire(title)?.let { it.groupValues[1].uppercase() to it.groupValues[2].trim() } ?: ("" to title.trim())

    fun join(letter: String, name: String): String {
        val n = name.trim().ifEmpty { "Программа" }
        return (if (letter.isEmpty()) n else "$letter · $n").take(60)
    }

    /**
     * Буква для показа на плашке у каждой программы. Своя буква из названия, иначе первая буква названия,
     * а если она уже занята — первая свободная латинская буква.
     */
    fun display(programs: List<Program>): Map<String, String> {
        val used = HashSet<String>()
        val out = LinkedHashMap<String, String>()
        val own = programs.associate { it.id to split(it.title).first }
        own.values.filter { it.isNotEmpty() }.forEach { used += it }
        for (p in programs) {
            val l = own.getValue(p.id)
            out[p.id] = l.ifEmpty {
                val first = split(p.title).second.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                val pick = if (first.isNotEmpty() && first !in used) first else ('A'..'Z').map { it.toString() }.firstOrNull { it !in used } ?: "?"
                used += pick
                pick
            }
        }
        return out
    }
}
