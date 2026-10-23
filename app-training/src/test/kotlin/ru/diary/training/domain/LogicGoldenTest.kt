package ru.diary.training.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import ru.diary.core.util.Js
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Сверка Kotlin-логики с результатами JS-прототипа на одинаковых входных данных. */
class LogicGoldenTest {
    private val today = Golden.TODAY
    private val data = Golden.baseData()
    private val catalog = ExerciseCatalog(emptyList())

    @Test
    fun suggestMatchesPrototype() {
        val cases = Golden["suggest"].jsonArray
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val o = c.jsonObject
            val id = o.getValue("id").jsonPrimitive.content
            val got = Logic.suggest(catalog, data.sessions, id, o.getValue("tg").jsonPrimitive.int, o.getValue("lo").jsonPrimitive.int, o.getValue("hi").jsonPrimitive.int)
            val exp = o.getValue("result").jsonObject
            assertEquals(exp.getValue("w").jsonPrimitive.double(), got.w, "w для $id")
            assertEquals(exp.getValue("r").jsonPrimitive.int, got.r, "r для $id")
            assertEquals(exp.getValue("hint").jsonPrimitive.content, got.hint, "hint для $id")
            assertEquals(exp["first"]?.jsonPrimitive?.booleanOrNull ?: false, got.first, "first для $id")
        }
    }

    @Test
    fun nextProgAndWeeklyStats() {
        assertEquals(Golden["nextProg"].jsonPrimitive.content, Logic.nextProg(data.programs, data.sessions))
        assertEquals(Golden["streak"].jsonPrimitive.int, Logic.streak(data.sessions, today))
        for (c in Golden["weekCounts"].jsonArray) {
            val ws = c.jsonObject.getValue("ws").jsonPrimitive.content
            val e = c.jsonObject.getValue("result").jsonObject
            val got = Logic.weekCounts(data.sessions, ws)
            assertEquals(e.getValue("gym").jsonPrimitive.int, got.gym, "gym $ws")
            assertEquals(e.getValue("volley").jsonPrimitive.int, got.volley, "volley $ws")
            assertEquals(e.getValue("friends").jsonPrimitive.int, got.friends, "friends $ws")
            assertEquals(e.getValue("other").jsonPrimitive.int, got.other, "other $ws")
            assertEquals(e.getValue("total").jsonPrimitive.int, got.total, "total $ws")
            assertEquals(e.getValue("t").jsonObject.mapValues { it.value.jsonPrimitive.int }, got.t, "t $ws")
        }
        for (c in Golden["weekTotals"].jsonArray) {
            val ws = c.jsonObject.getValue("ws").jsonPrimitive.content
            val e = c.jsonObject.getValue("result").jsonObject
            val got = Logic.weekTotals(data.sessions, ws)
            assertEquals(e.getValue("min").jsonPrimitive.int, got.min)
            assertEquals(e.getValue("kcal").jsonPrimitive.int, got.kcal)
        }
    }

    @Test
    fun planWeeksBlocksAndDeload() {
        assertEquals(Golden["planTotal"].jsonPrimitive.int, Logic.planTotal(data.plan))
        for (c in Golden["planWeek"].jsonArray) {
            val d = c.jsonObject.getValue("d").jsonPrimitive.content
            assertEquals(c.jsonObject.getValue("week").jsonPrimitive.int, Logic.planWeek(data.plan, d), "неделя плана на $d")
        }
        for (c in Golden["weekFlags"].jsonArray) {
            val o = c.jsonObject
            val w = o.getValue("w").jsonPrimitive.int
            assertEquals(o.getValue("deload").jsonPrimitive.boolean(), Logic.isDeload(data.plan, w), "разгрузка w=$w")
            assertEquals(o.getValue("test").jsonPrimitive.boolean(), Logic.isTest(data.plan, w), "тест w=$w")
            val block = o.getValue("block").let { if (it is JsonNull) null else it.jsonPrimitive.content }
            if (block != null) assertEquals(block, Logic.blockOf(data.plan, w).name, "блок w=$w")
        }
        assertEquals(Golden["deloadNow"].jsonPrimitive.boolean(), Logic.deloadNow(data.plan, today))
    }

    @Test
    fun planWithoutStartHasNoWeek() {
        assertNull(Logic.planWeek(data.plan.copy(start = ""), today))
        assertTrue(!Logic.deloadNow(data.plan.copy(start = ""), today))
    }

    @Test
    fun deloadWeekHalvesSets() {
        val e = Golden["deloadWeek5"].jsonObject
        val plan = Golden.basePlan("2026-02-16")
        assertEquals(e.getValue("planWeek").jsonPrimitive.int, Logic.planWeek(plan, today))
        assertTrue(Logic.deloadNow(plan, today))
        val a4 = Logic.buildActiveEx(catalog, data.sessions, plan, today, ProgramItem("bbsquat", 4, 5, 5))
        val a3 = Logic.buildActiveEx(catalog, data.sessions, plan, today, ProgramItem("dips", 3, 6, 10))
        assertEquals(e.getValue("active").jsonObject.getValue("sets").jsonArray.size, a4.sets.size)
        assertEquals(e.getValue("active3").jsonObject.getValue("sets").jsonArray.size, a3.sets.size)
        assertEquals(2, a4.sets.size)
        assertEquals(2, a3.sets.size) // ceil(3 / 2)
        assertEquals(4, a4.tg + 2)
    }

    @Test
    fun activeExercisesMatchPrototype() {
        for (c in Golden["buildActiveEx"].jsonArray) {
            val o = c.jsonObject
            val item = o.getValue("item").jsonObject
            val got = Logic.buildActiveEx(
                catalog, data.sessions, data.plan, today,
                ProgramItem(item.getValue("id").jsonPrimitive.content, item.getValue("sets").jsonPrimitive.int, item.getValue("lo").jsonPrimitive.int, item.getValue("hi").jsonPrimitive.int),
            )
            val e = o.getValue("result").jsonObject
            assertEquals(e.getValue("tg").jsonPrimitive.int, got.tg)
            assertEquals(e.getValue("hint").jsonPrimitive.content, got.hint)
            assertEquals(e.getValue("first").jsonPrimitive.booleanOrNull ?: false, got.first)
            val d = e.getValue("d").jsonObject
            assertEquals(d.getValue("name").jsonPrimitive.content, got.d.name)
            assertEquals(d.getValue("kind").jsonPrimitive.content, got.d.kind)
            assertEquals(d.getValue("step").jsonPrimitive.double(), got.d.step)
            assertEquals(d.getValue("rest").jsonPrimitive.int, got.d.rest)
            assertEquals(d.getValue("per").jsonPrimitive.content, got.d.per)
            val sets = e.getValue("sets").jsonArray
            assertEquals(sets.size, got.sets.size)
            sets.forEachIndexed { i, s ->
                assertEquals(s.jsonObject.getValue("w").jsonPrimitive.double(), got.sets[i].w)
                assertEquals(s.jsonObject.getValue("r").jsonPrimitive.int, got.sets[i].r)
            }
        }
    }

    @Test
    fun maxStatusMatchesPrototype() {
        val empty = Golden["maxStatusEmpty"].jsonObject
        val s0 = Logic.maxStatus(data.plan, emptyList(), today)
        assertEquals(empty.getValue("text").jsonPrimitive.content, s0.text)
        assertEquals(empty.getValue("due").jsonPrimitive.boolean(), s0.due)
        assertNull(s0.days)

        val maxes = listOf(MaxEntry("m1", "2026-02-20", "push", 25.0), MaxEntry("m2", "2026-03-17", "push", 30.0), MaxEntry("m3", "2026-01-10", "fs", 40.0))
        val e = Golden["maxStatus"].jsonObject
        val s = Logic.maxStatus(data.plan, maxes, today)
        assertEquals(e.getValue("text").jsonPrimitive.content, s.text)
        assertEquals(e.getValue("cnt").jsonPrimitive.int, s.cnt)
        assertEquals(e.getValue("days").jsonPrimitive.int, s.days)
        assertEquals(e.getValue("due").jsonPrimitive.boolean(), s.due)
        assertEquals(e.getValue("testWeek").jsonPrimitive.boolean(), s.testWeek)
        assertEquals(Golden["bestPush"].jsonPrimitive.double(), Logic.bestOf(data.plan, maxes, "push"))
        // «Меньше — лучше»: лучшим считается минимальное значение
        assertEquals(Golden["bestFs"].jsonPrimitive.double(), Logic.bestOf(data.plan, maxes, "fs"))
    }

    @Test
    fun helpersMatchPrototype() {
        assertEquals(Golden["volleyNote"].jsonPrimitive.content, Logic.volleyNote(data.plan, data.sessions, today))
        for (c in Golden["fmt"].jsonArray) {
            val o = c.jsonObject
            assertEquals(o.getValue("s").jsonPrimitive.content, Js.fmt(o.getValue("n").jsonPrimitive.double()), "fmt ${o["n"]}")
        }
        for (c in Golden["parseNum"].jsonArray) {
            val o = c.jsonObject
            assertEquals(o.getValue("n").jsonPrimitive.double(), Js.parseNum(o.getValue("v")), "parseNum ${o["v"]}")
        }
        for (c in Golden["plural"].jsonArray) {
            val o = c.jsonObject
            assertEquals(o.getValue("s").jsonPrimitive.content, Js.plural(o.getValue("n").jsonPrimitive.int, "подход", "подхода", "подходов"))
        }
        for (c in Golden["setReps"].jsonArray) {
            val tr = c.jsonObject.getValue("tr").jsonObject
            val e = c.jsonObject.getValue("result").jsonObject
            val got = Logic.setReps(tr.getValue("base").jsonPrimitive.int, tr.getValue("pctLo").jsonPrimitive.int, tr.getValue("pctHi").jsonPrimitive.int)
            assertEquals(e.getValue("lo").jsonPrimitive.int to e.getValue("hi").jsonPrimitive.int, got)
        }
        for (c in Golden["progBrief"].jsonArray) {
            val id = c.jsonObject.getValue("id").jsonPrimitive.content
            assertEquals(c.jsonObject.getValue("text").jsonPrimitive.content, Logic.progBrief(data.programs.first { it.id == id }))
        }
        assertEquals(65.0, Logic.latestWeight(emptyMap()))
        assertEquals(Golden["weights"].jsonObject.getValue("latest").jsonPrimitive.double(), Logic.latestWeight(data.wlog))
    }

    private fun kotlinx.serialization.json.JsonPrimitive.double(): Double = content.toDouble()
    private fun kotlinx.serialization.json.JsonPrimitive.boolean(): Boolean = content == "true"
}
