package ru.diary.training.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusclesTest {
    @Test fun everyBuiltinExerciseHasMusclesDrawnOnFigure() {
        val drawn = (Muscles.front + Muscles.back).map { it.id }.toSet()
        for (d in BuiltinData.allExercises) {
            val l = assertNotNull(Muscles.forEx(d.id), "мышцы для ${d.id}")
            assertTrue(l.primary.isNotEmpty(), "основные мышцы для ${d.id}")
            (l.primary + l.secondary).forEach { m ->
                assertTrue(m in drawn, "$m (${d.id}) есть на фигуре")
                assertTrue(Muscles.name(m) != m, "у $m есть русское название")
            }
        }
    }

    @Test fun gymExercisesAreInCatalog() {
        val c = ExerciseCatalog(emptyList())
        assertEquals("Жим штанги лёжа", c.nameOf("bench"))
        assertEquals("Подъём гантелей на бицепс", c.nameOf("curl"))
        assertEquals(BuiltinData.allExercises.size, BuiltinData.allExercises.map { it.id }.toSet().size, "id не повторяются")
        assertTrue(BuiltinData.gymExercises.size >= 20)
    }

    @Test fun figuresAreParsed() {
        assertTrue(Muscles.front.any { it.id == "chest" } && Muscles.back.any { it.id == "glutes" })
        assertTrue((Muscles.front + Muscles.back).all { s -> s.polygons.all { it.size >= 6 && it.size % 2 == 0 } })
        assertNull(Muscles.forEx("c-custom"))
    }

    @Test fun mainSidePicksWhereThePrimaryMusclesAre() {
        assertEquals(true, Muscles.mainSideIsFront(Muscles.forEx("dbbench")!!))
        assertEquals(false, Muscles.mainSideIsFront(Muscles.forEx("pullup")!!))
    }
}
