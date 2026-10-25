package ru.diary.nutrition.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FoodsTest {
    @Test fun databaseLoads() {
        assertTrue(Foods.all.size >= 100)
        val breast = Foods.byId.getValue("chicken-breast")
        assertEquals(165.0, breast.per100.kcal); assertEquals(31.0, breast.per100.p)
        assertEquals(1.2, Foods.byId.getValue("tomato").per100.fiber)
        assertTrue(Foods.visible.none { it.id == "water" })
        // У каждого продукта названия уникальны, а категория известна.
        assertEquals(Foods.all.size, Foods.all.map { it.name }.toSet().size)
        val cats = Foods.CATS.map { it.first }.toSet() + "other"
        assertTrue(Foods.all.all { it.cat in cats })
    }

    @Test fun everyBuiltinDishHasComposition() {
        for (d in Catalog.builtin) {
            val parts = Foods.parts(d.id)
            assertTrue(parts.isNotEmpty(), "нет состава у ${d.id}")
            assertEquals(100, Foods.shares(parts).sumOf { it.second }, d.id)
            assertNotNull(d.grams); assertNotNull(d.fiber)
            // Калории порции посчитаны по составу.
            assertEquals(ru.diary.core.util.Js.round(Foods.total(parts).kcal).toInt(), d.kcal)
        }
    }

    @Test fun bakedFishIsMostlyFishAndPotato() {
        val shares = Foods.shares(Foods.parts("baked-fish")).associate { it.first.id to it.second }
        assertEquals(52, shares["potato-baked"]); assertEquals(41, shares["pollock"])
    }

    @Test fun weighingScalesPer100g() {
        val tomato = Weigh.food(Foods.byId.getValue("tomato"))
        val e = Weigh.eat(tomato, 150, "2026-10-25", "13:00", 1L)
        assertEquals(27, e.kcal); assertEquals(1.8, e.fiber); assertTrue(e.byWeight); assertEquals("150 г", amountLabel(e))
        val plov = Weigh.dish(Catalog.builtin.first { it.id == "plov" })!!
        val p = Weigh.eat(plov, 100, "2026-10-25", "14:00", 2L)
        assertEquals(ru.diary.core.util.Js.round(Foods.per100(plov.parts).kcal).toInt(), p.kcal)
        val t = NutritionLogic.totals(NutritionData(log = listOf(e, p)), "2026-10-25")
        assertEquals(e.kcal + p.kcal, t.kcal)
        assertEquals(Weigh.round1(1.8 + (p.fiber ?: 0.0)), t.fiber)
    }

    @Test fun oldDishEntriesGetMacrosFromCatalog() {
        val d = Catalog.builtin.first { it.id == "oatmeal" }
        val old = Entry("x", "2026-10-25", "08:00", d.id, d.name, 1.0, d.kcal, d.p, true)
        val t = NutritionLogic.totals(NutritionData(log = listOf(old)), "2026-10-25")
        assertEquals(d.f, t.fat); assertEquals(d.fiber, t.fiber)
    }
}
