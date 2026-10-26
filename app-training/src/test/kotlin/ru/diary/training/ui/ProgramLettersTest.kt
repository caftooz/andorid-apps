package ru.diary.training.ui

import org.junit.Test
import ru.diary.training.domain.Program
import kotlin.test.assertEquals

class ProgramLettersTest {
    private fun p(id: String, title: String) = Program(id, title, true, emptyList(), emptyList())

    @Test fun splitsLetterFromTitle() {
        assertEquals("A" to "ноги и жимы", ProgramLetters.split("A · ноги и жимы"))
        assertEquals("" to "Дом без инвентаря", ProgramLetters.split("Дом без инвентаря"))
        assertEquals("" to "5x5 — сила", ProgramLetters.split("5x5 — сила"))
    }

    @Test fun joinsAndKeepsName() {
        assertEquals("D · кардио", ProgramLetters.join("D", "кардио"))
        assertEquals("кардио", ProgramLetters.join("", "кардио"))
        assertEquals("B · Программа", ProgramLetters.join("B", "  "))
    }

    @Test fun sortsByLetterThenName() {
        val list = listOf(p("1", "C · ноги"), p("2", "A · верх"), p("3", "B · спина"), p("4", "Дом"))
        assertEquals(listOf("2", "3", "1", "4"), ProgramLetters.sorted(list).map { it.id })
        // Сменили букву «C» на «0»? — программа встаёт первой.
        val renamed = list.map { if (it.id == "1") it.copy(title = ProgramLetters.join("0", "ноги")) else it }
        assertEquals(listOf("1", "2", "3", "4"), ProgramLetters.sorted(renamed).map { it.id })
    }

    @Test fun displayLettersAreUnique() {
        val l = ProgramLetters.display(listOf(p("1", "A · ноги"), p("2", "Аэробика"), p("3", "B · спина"), p("4", "Бег")))
        assertEquals("A", l["1"]); assertEquals("B", l["3"])
        assertEquals("А", l["2"]) // кириллическая «А» не совпадает с латинской
        assertEquals("Б", l["4"])
        val dup = ProgramLetters.display(listOf(p("1", "A · ноги"), p("2", "A · верх")))
        assertEquals("A", dup["1"]); assertEquals("A", dup["2"])
        val free = ProgramLetters.display(listOf(p("1", "A · ноги"), p("2", "Атлетика")))
        assertEquals("А", free["2"])
    }
}
