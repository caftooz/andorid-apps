package ru.diary.training.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GuidesTest {
    @Test fun everyBuiltinHomeExerciseHasGuide() {
        for (plan in listOf(BuiltinData.defaultPlan(), BuiltinData.girlPlan())) {
            for (t in plan.tracks.filter { it.kind == "timed" }) {
                assertNotNull(Guides.forTrack(t.id), "общая инструкция для ${t.id}")
                (t.steps.map { it.n } + t.items.map { it.n }).forEach { assertNotNull(Guides.forItem(it), "инструкция для «$it»") }
            }
        }
    }

    @Test fun lookupIgnoresCaseAndYo() {
        assertNotNull(Guides.forItem("  кик-апы "))
        assertEquals(null, Guides.forItem("Неизвестное упражнение"))
    }

    @Test fun parsesMarkup() {
        val b = Guides.parse("Вступление\n\n## Шаги\n1. Встаньте\n2. Сядьте\n- Совет")
        assertEquals(
            listOf(
                Guides.Block.Para("Вступление"), Guides.Block.Heading("Шаги"),
                Guides.Block.Item("1.", "Встаньте"), Guides.Block.Item("2.", "Сядьте"), Guides.Block.Item("•", "Совет"),
            ),
            b,
        )
        assertTrue(Guides.parse(Guides.forTrack("handstand")!!).any { it is Guides.Block.Heading })
    }
}
