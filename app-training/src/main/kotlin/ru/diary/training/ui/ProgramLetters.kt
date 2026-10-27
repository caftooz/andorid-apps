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
     * Программы по алфавиту: по букве на плашке, затем по названию. Так после смены буквы список
     * на главной, в плане и очередь чередования сразу идут в новом порядке.
     */
    fun sorted(programs: List<Program>): List<Program> {
        val letters = display(programs)
        return programs.sortedWith(compareBy<Program>({ letters.getValue(it.id) }, { split(it.title).second.lowercase() }))
    }

    /** Программы очереди (`rot`) в порядке чередования — по алфавиту букв. */
    fun queue(programs: List<Program>): List<Program> = sorted(programs).filter { it.rot }

    /**
     * Раздать буквы очереди по порядку `order`: первая программа — первая свободная буква и т. д.
     * Буквы программ вне очереди не трогаем и не занимаем.
     */
    fun relabel(programs: List<Program>, order: List<String>): List<Program> {
        val inQueue = order.toSet()
        val used = programs.filter { it.id !in inQueue }.map { split(it.title).first }.filter { it.isNotEmpty() }.toSet()
        val free = ('A'..'Z').map { it.toString() }.filter { it !in used }
        val letter = order.withIndex().associate { (i, id) -> id to free.getOrElse(i) { "" } }
        return programs.map { p -> letter[p.id]?.let { l -> p.copy(title = join(l, split(p.title).second)) } ?: p }
    }

    /** Сдвинуть программу в очереди на шаг (`dir` = −1 выше, +1 ниже); буквы перераздаются по новому порядку. */
    fun move(programs: List<Program>, id: String, dir: Int): List<Program> {
        val q = queue(programs).map { it.id }.toMutableList()
        val i = q.indexOf(id)
        val j = i + dir
        if (i < 0 || j !in q.indices) return programs
        q[i] = q[j].also { q[j] = q[i] }
        return relabel(programs, q)
    }

    /** Поставить программу в конец очереди или убрать из неё (тогда буква снимается). */
    fun setInQueue(programs: List<Program>, id: String, on: Boolean): List<Program> {
        val order = queue(programs).map { it.id }.filter { it != id } + if (on) listOf(id) else emptyList()
        val updated = programs.map { p ->
            if (p.id != id) p else if (on) p.copy(rot = true) else p.copy(rot = false, title = join("", split(p.title).second))
        }
        return relabel(updated, order)
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
