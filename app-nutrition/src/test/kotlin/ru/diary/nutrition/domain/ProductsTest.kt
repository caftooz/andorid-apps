package ru.diary.nutrition.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductsTest {
    @Test fun parsesOpenFoodFactsProduct() {
        val body = """{"code":"4607001771111","status":1,"product":{"product_name":"Tvorog","product_name_ru":"Творог 5%","brands":"Простоквашино, Danone",
            "product_quantity":"180","nutriments":{"energy-kcal_100g":121,"proteins_100g":"17","fat_100g":5,"carbohydrates_100g":1.8}}}"""
        val r = OpenFoodFacts.parse("4607001771111", body, 1L) as OffResult.Found
        assertTrue(r.complete)
        assertEquals("Творог 5%", r.product.name)
        assertEquals("Простоквашино", r.product.brand)
        assertEquals(121.0, r.product.kcal); assertEquals(17.0, r.product.p); assertEquals(5.0, r.product.f); assertEquals(1.8, r.product.c)
        assertEquals(180, r.product.packG)
        assertEquals("off", r.product.source)
    }

    @Test fun kilojoulesAndMissingData() {
        val kj = OpenFoodFacts.parse("12345670", """{"status":1,"product":{"product_name":"Сок","nutriments":{"energy_100g":184}}}""", 0) as OffResult.Found
        assertEquals(44.0, kj.product.kcal)
        val noKcal = OpenFoodFacts.parse("12345670", """{"status":1,"product":{"product_name":"Сок","nutriments":{}}}""", 0) as OffResult.Found
        assertEquals(false, noKcal.complete)
        assertEquals(OffResult.NotFound, OpenFoodFacts.parse("12345670", """{"status":0,"status_verbose":"product not found"}""", 0))
        assertEquals(OffResult.NotFound, OpenFoodFacts.parse("12345670", "<html>", 0))
    }

    @Test fun barcodeNormalization() {
        assertEquals("4607001771111", Products.normalize(" 4607001771111 "))
        assertEquals("12345670", Products.normalize("12345670"))
        assertNull(Products.normalize("12345"))
        assertNull(Products.normalize("46070017711AB"))
    }

    @Test fun eatScalesPer100g() {
        val p = Product("4607001771111", "Творог", "", 121.0, 17.0, 5.0, 1.8, 180, "off", 0)
        val e = Products.eat(p, 180, "2026-10-24", "09:00", 1000L)
        assertEquals(218, e.kcal); assertEquals(31, e.protein); assertEquals(9, e.fat); assertEquals(3, e.carbs)
        assertTrue(e.isProduct); assertEquals("180 г", amountLabel(e))
        val t = NutritionLogic.totals(NutritionData(log = listOf(e)), "2026-10-24")
        assertEquals(9, t.fat); assertEquals(3, t.carbs); assertEquals(218, t.kcal)
    }

    @Test fun manualValidation() {
        assertNull(Products.manual("12345670", "", "", "100", "", "", "", "", 0))
        assertNull(Products.manual("12345670", "Сыр", "", "", "", "", "", "", 0))
        val m = Products.manual("12345670", " Сыр ", "Брест", "350,5", "25", "27", "0", "200", 5)!!
        assertEquals("Сыр", m.name); assertEquals(350.5, m.kcal); assertEquals(200, m.packG); assertEquals("user", m.source)
    }

    @Test fun exportImportKeepsProductsAndMacros() {
        val p = Product("4607001771111", "Творог", "Простоквашино", 121.0, 17.0, 5.0, 1.8, 180, "off", 7)
        val e = Products.eat(p, 100, "2026-10-24", "09:00", 1000L)
        val text = NutritionTransfer.exportText(NutritionData(log = listOf(e), products = listOf(p)), "2026-10-24T00:00:00Z")
        val d = NutritionTransfer.parseImport(text)!!
        assertEquals(listOf(p), d.products)
        assertEquals(e, d.log.single())
        // Записи без жиров/углеводов (вне плана, старые) экспортируются без новых полей, как в веб-версии.
        val oop = NutritionLogic.outOfPlan("Кафе", "500", "20", "2026-10-24", "12:00", 1L)
        val obj = NutritionTransfer.entryJson(oop)
        assertTrue("fat" !in obj && "carbs" !in obj && "fiber" !in obj)
        // Записи блюд теперь несут жиры, углеводы и клетчатку из состава.
        val dish = NutritionLogic.eat(Catalog.builtin.first(), "2026-10-24", "12:00", 1.0, 1L)
        assertEquals(dish, NutritionTransfer.cleanEntry(NutritionTransfer.entryJson(dish)))
        val merged = NutritionTransfer.applyImport(NutritionData(products = listOf(p)), d, NutritionTransfer.Mode.Merge)
        assertEquals(1, merged.products.size)
    }
}
