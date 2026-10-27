package ru.diary.training.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PresetsTest {
    @Test fun presetsUseOnlyKnownExercises() {
        val c = ExerciseCatalog(emptyList())
        assertTrue(Presets.all.size >= 8)
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
        for (p in Presets.all) {
            assertTrue(p.items.isNotEmpty() && p.warm.isNotEmpty(), p.id)
            p.items.forEach { i ->
                assertNotNull(c.defOf(i.id), "${p.id}: ${i.id}")
                assertTrue(i.sets in 1..10 && i.lo in 1..999 && i.hi >= i.lo, "${p.id}: ${i.id}")
            }
        }
    }

    @Test fun presetBecomesProgram() {
        val p = Presets.byId("back-biceps")!!
        val prog = Presets.toProgram(p, "p-1", rot = true)
        assertEquals("Спина и бицепс", prog.title)
        assertEquals(p.items, prog.items)
    }
}
