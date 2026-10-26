package ru.diary.training.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutLogicTest {
    private val today = Golden.TODAY
    private val now = 1_800_000_000_000L

    private fun workout(progId: String? = "A"): ActiveWorkout =
        Logic.startWorkout(Golden.baseData().copy(plan = Golden.basePlan("2026-03-16")), progId, today, now)

    @Test
    fun startFromProgramUsesItsWarmupAndExercises() {
        val a = workout("A")
        assertEquals("A · ноги и жимы", a.title)
        assertEquals(8, a.ex.size)
        assertEquals(BuiltinData.warmGym, a.warmList)
        val free = workout(null)
        assertEquals("Своя тренировка", free.title)
        assertTrue(free.ex.isEmpty())
        assertEquals(BuiltinData.warmGen, free.warmList)
    }

    @Test
    fun stepsRespectExerciseStepAndDoneSets() {
        var a = workout("A")
        val i = a.ex.indexOfFirst { it.id == "bbsquat" }
        val before = a.ex[i].sets[0].w
        a = Logic.stepSet(a, i, 0, 'w', 1)
        assertEquals(before + 2.5, a.ex[i].sets[0].w)
        a = Logic.stepSet(a, i, 0, 'w', -1)
        a = Logic.stepSet(a, i, 0, 'w', -1)
        assertTrue(a.ex[i].sets[0].w >= 0.0, "вес не уходит в минус")
        val done = Logic.toggleDone(a, i, 0)
        assertEquals(done, Logic.stepSet(done, i, 0, 'w', 1), "выполненный подход не меняется")

        val t = a.ex.indexOfFirst { it.id == "hollow" }
        val r0 = a.ex[t].sets[0].r
        assertEquals(r0 + 5, Logic.stepSet(a, t, 0, 'r', 1).ex[t].sets[0].r, "для упражнений на время шаг 5 секунд")
    }

    @Test
    fun doneCopiesWeightToNextUntouchedSetOnly() {
        var a = workout("A")
        val i = a.ex.indexOfFirst { it.id == "bbsquat" }
        a = Logic.stepSet(a, i, 0, 'w', 1)
        a = Logic.stepSet(a, i, 0, 'w', 1)
        a = Logic.toggleDone(a, i, 0)
        assertEquals(a.ex[i].sets[0].w, a.ex[i].sets[1].w)
        // вручную изменённый подход не перезаписывается
        val j = Logic.stepSet(workout("A"), i, 1, 'w', 1)
        val moved = Logic.stepSet(j, i, 0, 'w', 1)
        assertEquals(j.ex[i].sets[1].w, Logic.toggleDone(moved, i, 0).ex[i].sets[1].w)
    }

    @Test
    fun feedbackAdjustsNextSetByStep() {
        var a = workout("A")
        val i = a.ex.indexOfFirst { it.id == "dips" }
        a = Logic.toggleDone(a, i, 0)
        val w0 = a.ex[i].sets[0].w
        val easy = Logic.setFeedback(a, i, 0, "easy")
        assertEquals("easy", easy.ex[i].sets[0].fb)
        assertEquals(w0 + 2.5, easy.ex[i].sets[1].w)
        val hard = Logic.setFeedback(a, i, 0, "hard")
        assertEquals(maxOf(0.0, w0 - 2.5), hard.ex[i].sets[1].w)
        assertNull(Logic.setFeedback(easy, i, 0, "easy").ex[i].sets[0].fb, "повторное нажатие снимает отметку")
    }

    @Test
    fun finishKeepsOnlyDoneSets() {
        var a = workout("A")
        a = Logic.toggleDone(a, 0, 0)
        a = Logic.toggleDone(a, 0, 1)
        a = Logic.toggleDone(a, 2, 0)
        val s = Logic.finishSession(a, Logic.Finish(rpe = 4, pain = listOf("Колено"), note = "ок", kcal = "350"), now + 45 * 60_000L + 20_000)
        assertEquals(2, s.ex.size)
        assertEquals(listOf(2, 1), s.ex.map { it.sets.size })
        assertEquals(45, s.min)
        assertEquals(350, s.kcal)
        assertEquals("gym", s.type)
        assertEquals("A", s.prog)
        assertEquals(4, s.rpe)
        assertEquals(1, Logic.finishSession(a, Logic.Finish(), now).min, "минимум одна минута")
        assertNull(Logic.finishSession(a, Logic.Finish(kcal = "0"), now).kcal)
    }

    @Test
    fun restUsesExerciseDefaultOnlyInAutoMode() {
        val a = workout("A")
        val squat = a.ex.first { it.id == "bbsquat" }
        assertEquals(150, Logic.restSeconds(squat, true, 90))
        assertEquals(90, Logic.restSeconds(squat, false, 90))
        assertEquals(90, Logic.restSeconds(a.ex.first { it.id == "dips" }, true, 90), "без своего значения берётся общее")
    }

    @Test
    fun quickSessionRules() {
        assertNull(Logic.quickSession("other", Logic.QuickForm(name = "  "), today, now), "для «другого» нужно название")
        val v = assertNotNull(Logic.quickSession("volley", Logic.QuickForm(dur = 90, kcal = "480,4"), today, now))
        assertEquals(480, v.kcal)
        assertEquals(today, v.date)
        val o = assertNotNull(Logic.quickSession("other", Logic.QuickForm(name = " Бег ", dur = 30, day = "2026-03-17"), today, now))
        assertEquals("Бег", o.title)
        assertEquals("2026-03-17", o.date)
        val t = assertNotNull(Logic.quickSession("t:stretch", Logic.QuickForm(dur = 25, int = 5), today, now))
        assertEquals("track" to "stretch", t.type to t.tid)
        assertEquals(0, t.rpe, "у домашних занятий нагрузка не задаётся")
    }

    @Test
    fun trackSetsAreAddedAndUndone() {
        var list = Logic.addTrackSet(emptyList(), "pullday", 5, today, now)
        list = Logic.addTrackSet(list, "pullday", 4, today, now)
        assertEquals(listOf(5, 4), Logic.setsToday(list, "pullday", today))
        assertEquals(1, list.size)
        list = Logic.undoTrackSet(list, "pullday", today)
        assertEquals(listOf(5), Logic.setsToday(list, "pullday", today))
        list = Logic.undoTrackSet(list, "pullday", today)
        assertTrue(list.isEmpty(), "пустая запись дня удаляется")
        assertEquals(emptyList(), Logic.setsToday(list, "pullday", today))
    }

    @Test
    fun pairedTrackSetsGoTogether() {
        val plan = BuiltinData.defaultPlan()
        val pull = plan.tracks.first { it.id == "pullday" }.copy(base = 10)
        assertEquals(5 to null, Logic.trackReps(pull, plan), "без связки — только подтягивания")
        val both = PlanEdit.setTrackPair(pull, "push", plan, listOf(MaxEntry("m1", today, "push", 30.0)))
        assertEquals("push" to 30, both.pair to both.base2, "база второго упражнения — последний замер")
        val (h1, h2) = Logic.trackReps(both, plan)
        assertEquals(5 to 15, h1 to h2)

        var list = Logic.addTrackSet(emptyList(), "pullday", h1, today, now, h2)
        list = Logic.addTrackSet(list, "pullday", h1, today, now, h2)
        assertEquals(listOf(5, 5), Logic.setsToday(list, "pullday", today))
        assertEquals(listOf(15, 15), Logic.pairSetsToday(list, "pullday", today))
        list = Logic.undoTrackSet(list, "pullday", today)
        assertEquals(listOf(5), Logic.setsToday(list, "pullday", today))
        assertEquals(listOf(15), Logic.pairSetsToday(list, "pullday", today), "отмена убирает подход из обоих")

        val back = Cleaning.cleanSession(Transfer.sessionJson(list.single()))
        assertEquals(listOf(15), back?.sl2, "второе упражнение переживает экспорт")
        assertEquals("", PlanEdit.setTrackPair(both, "", plan, emptyList()).pair)
    }

    @Test
    fun rotationFollowsLastWorkout() {
        val progs = BuiltinData.defaultPrograms()
        assertEquals("A", Logic.nextProg(progs, emptyList()))
        val s = { id: String, prog: String, d: String ->
            Session(id, d, "gym", prog, "", 1, 50, 3, emptyList(), "", emptyList())
        }
        assertEquals("B", Logic.nextProg(progs, listOf(s("1", "A", "2026-03-01"))))
        assertEquals("A", Logic.nextProg(progs, listOf(s("1", "A", "2026-03-01"), s("2", "C", "2026-03-05"))))
        assertEquals("B", Logic.nextProg(progs.map { if (it.id == "C") it.copy(rot = false) else it }, listOf(s("1", "A", "2026-03-01"))))
        assertEquals("X", Logic.nextProg(listOf(progs[0].copy(id = "X", rot = false)), emptyList()))
        assertNull(Logic.nextProg(emptyList(), emptyList()))
    }
}
