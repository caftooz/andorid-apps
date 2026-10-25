package ru.diary.nutrition.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoMealTest {
    @Test fun parsesModelAnswer() {
        val m = PhotoPrompt.parse(
            """{"dish":"Гречка с курицей","items":[{"food":"buckwheat","grams":180},{"food":"chicken-breast","grams":120},
               {"food":"unknown-food","grams":50},{"food":"buckwheat","grams":20},{"food":"cucumber","grams":0}],"note":"Масло не видно"}""",
        )!!
        assertEquals("Гречка с курицей", m.name)
        assertEquals(listOf("buckwheat" to 200, "chicken-breast" to 120), m.items.map { it.food.id to it.grams })
        assertEquals(320, m.grams)
        assertEquals("Масло не видно", m.note)
        // 200 г гречки (92 ккал/100 г) + 120 г грудки (165 ккал/100 г)
        assertEquals(382, kotlin.math.round(m.total.kcal).toInt())
    }

    @Test fun toleratesFencesAndGarbage() {
        assertEquals(1, PhotoPrompt.parse("```json\n{\"dish\":\"Яблоко\",\"items\":[{\"food\":\"apple\",\"grams\":150}],\"note\":\"\"}\n```")!!.items.size)
        assertNull(PhotoPrompt.parse("извините, не вижу еды"))
    }

    @Test fun schemaListsEveryVisibleFood() {
        val schema = PhotoPrompt.schema().toString()
        assertTrue(Foods.visible.all { "\"${it.id}\"" in schema })
        assertTrue("\"water\"" !in schema)
        assertTrue(PhotoPrompt.instruction().contains("buckwheat — Гречка варёная"))
    }

    @Test fun entrySumsProducts() {
        val m = PhotoMeal("", listOf(PhotoItem(Foods.byId.getValue("tomato"), 150), PhotoItem(Foods.byId.getValue("cucumber"), 100)), "")
        val e = m.toEntry("2026-10-25", "13:00", 1L)
        assertEquals("Блюдо по фото", e.name)
        assertEquals(250.0, e.portion); assertTrue(e.byWeight)
        assertEquals(42, e.kcal) // 27 + 15
        assertEquals(Weigh.round1(1.8 + 0.5), e.fiber)
    }
}
