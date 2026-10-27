package ru.diary.training.ui

import org.junit.Test
import ru.diary.training.domain.Program
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    private fun q(id: String, title: String, rot: Boolean = true) = Program(id, title, rot, emptyList(), emptyList())

    @Test fun moveSwapsQueueOrderAndLetters() {
        val list = listOf(q("a", "A · ноги"), q("b", "B · спина"), q("c", "C · калистеника"), q("h", "D · дом", rot = false))
        val moved = ProgramLetters.move(list, "c", -1)
        assertEquals(listOf("a", "c", "b"), ProgramLetters.queue(moved).map { it.id })
        assertEquals("B · калистеника", moved.first { it.id == "c" }.title)
        assertEquals("C · спина", moved.first { it.id == "b" }.title)
        assertEquals("D · дом", moved.first { it.id == "h" }.title, "программы вне очереди не трогаем")
        assertEquals(list, ProgramLetters.move(list, "a", -1), "выше первой не двигается")
    }

    @Test fun queueSkipsLettersOfOtherPrograms() {
        val list = listOf(q("h", "A · дом", rot = false), q("x", "Спина", rot = false))
        val added = ProgramLetters.setInQueue(list, "x", true)
        assertEquals("B · Спина", added.first { it.id == "x" }.title)
        val removed = ProgramLetters.setInQueue(added, "x", false)
        assertEquals("Спина", removed.first { it.id == "x" }.title)
        assertTrue(ProgramLetters.queue(removed).isEmpty())
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
