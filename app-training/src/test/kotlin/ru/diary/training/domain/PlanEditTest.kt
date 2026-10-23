package ru.diary.training.domain

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PlanEditTest {
    private val plan = BuiltinData.defaultPlan()

    @Test
    fun linesMatchPrototype() {
        for (c in Golden["lines"].jsonArray) {
            val o = c.jsonObject
            val parts = o.getValue("parts").jsonArray.size
            val exp = o.getValue("result").jsonArray.map { r -> o.getValue("parts").jsonArray.map { r.jsonObject.getValue(it.jsonPrimitive.content).jsonPrimitive.content } }
            assertEquals(exp, PlanEdit.linesOf(o.getValue("text").jsonPrimitive.content, parts))
        }
        val joined = PlanEdit.joinLines(listOf(listOf("a", "b", "c"), listOf("x", "", ""), listOf("y", "z", "")))
        assertEquals(Golden["joinLines"].jsonPrimitive.content, joined)
    }

    @Test
    fun blocksStayWithinLimits() {
        var p = plan
        repeat(200) { p = PlanEdit.stepBlockWeeks(p, 0, 1) }
        assertEquals(104, p.blocks[0].weeks)
        repeat(200) { p = PlanEdit.stepBlockWeeks(p, 0, -1) }
        assertEquals(1, p.blocks[0].weeks)
        val single = plan.copy(blocks = plan.blocks.take(1))
        assertSame(single, PlanEdit.removeBlock(single, 0), "последний блок удалить нельзя")
        val moved = PlanEdit.moveBlock(plan, 0, 1)
        assertEquals(listOf("Сила", "База", "Мощность и навыки"), moved.blocks.map { it.name })
        assertSame(plan, PlanEdit.moveBlock(plan, 0, -1))
    }

    @Test
    fun deloadAndTestWeeksAreBounded() {
        var p = plan
        repeat(40) { p = PlanEdit.stepDeload(p, 1) }
        assertEquals(20, p.deloadEvery)
        repeat(40) { p = PlanEdit.stepTest(p, -1) }
        assertEquals(0, p.testEvery)
        assertEquals(false, Logic.isTest(p, 4), "при 0 тесты отключены")
    }

    @Test
    fun trackStepsKeepRangeConsistent() {
        var t = PlanEdit.newTrack("sets")
        t = PlanEdit.stepTrack(t, PlanEdit.TrackField.PctLo, 20)
        assertEquals(100, t.pctLo)
        assertEquals(100, t.pctHi, "верхняя граница подтягивается")
        t = PlanEdit.stepTrack(t, PlanEdit.TrackField.PctHi, -50)
        assertEquals(5, t.pctHi)
        assertEquals(5, t.pctLo, "нижняя граница не выше верхней")
        assertEquals(30, PlanEdit.stepTrack(t, PlanEdit.TrackField.DayTarget, 100).dayTarget)
        assertEquals(5, PlanEdit.stepTrack(t, PlanEdit.TrackField.Dur, -100).dur)
        assertEquals(7, PlanEdit.stepTrack(t, PlanEdit.TrackField.Goal, 100).goal)
    }

    @Test
    fun trackMetricTakesLastMaxAsBase() {
        val maxes = listOf(MaxEntry("1", "2026-01-01", "pull", 9.0), MaxEntry("2", "2026-02-01", "pull", 12.4))
        val t = PlanEdit.setTrackMetric(plan.tracks.first { it.id == "pullday" }, "pull", plan, maxes)
        assertEquals("pull", t.metric)
        assertEquals(12, t.base)
        val pullday = plan.tracks.first { it.id == "pullday" }
        assertEquals(pullday.base, PlanEdit.setTrackMetric(pullday, "", plan, maxes).base, "без показателя база не меняется")
    }

    @Test
    fun metricEditing() {
        val p = PlanEdit.editMetric(plan, "push", goal = "  ", dir = -3)
        val m = p.metrics.first { it.id == "push" }
        assertEquals(null, m.goal)
        assertEquals(-1, m.dir)
        assertEquals(12.5, PlanEdit.editMetric(plan, "push", goal = "12,5").metrics.first { it.id == "push" }.goal)
    }

    @Test
    fun rulesRoundTrip() {
        val text = PlanEdit.rulesToText(plan.rules)
        assertEquals(plan.rules, PlanEdit.rulesFromText(text))
    }
}
