package ru.diary.nutrition.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Ответ Open Food Facts, разобранный в продукт. */
sealed interface OffResult {
    /** Продукт найден; `complete = false`, если в базе нет калорийности и её нужно ввести с этикетки. */
    data class Found(val product: Product, val complete: Boolean) : OffResult
    data object NotFound : OffResult
}

/**
 * Разбор ответа `api/v2/product/<код>.json` открытой базы Open Food Facts (https://openfoodfacts.org).
 * Берутся название (по-русски, если есть), бренд, КБЖУ на 100 г и вес упаковки.
 */
object OpenFoodFacts {
    const val FIELDS = "product_name,product_name_ru,generic_name_ru,brands,nutriments,product_quantity,quantity"

    fun url(code: String) = "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS"

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }
        ?.takeIf { it.isFinite() && it >= 0 }

    fun parse(code: String, body: String, now: Long): OffResult {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return OffResult.NotFound
        if ((root["status"] as? JsonPrimitive)?.intOrNull == 0) return OffResult.NotFound
        val p = root["product"] as? JsonObject ?: return OffResult.NotFound
        val n = p["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
        val name = p.str("product_name_ru") ?: p.str("product_name") ?: p.str("generic_name_ru") ?: ""
        // Калории: сначала ккал, иначе пересчёт из кДж.
        val kcal = n.num("energy-kcal_100g") ?: n.num("energy_100g")?.let { it / 4.184 }
        val brand = p.str("brands")?.split(',')?.firstOrNull()?.trim().orEmpty()
        val pack = p.num("product_quantity")?.takeIf { it in 1.0..20000.0 }?.toInt()
        val prod = Product(
            code, name.take(120), brand.take(80), round1(kcal ?: 0.0).coerceAtMost(1000.0),
            round1(n.num("proteins_100g") ?: 0.0).coerceAtMost(100.0), round1(n.num("fat_100g") ?: 0.0).coerceAtMost(100.0),
            round1(n.num("carbohydrates_100g") ?: 0.0).coerceAtMost(100.0), pack, "off", now,
        )
        return OffResult.Found(prod, complete = name.isNotEmpty() && kcal != null)
    }

    private fun round1(v: Double) = kotlin.math.round(v * 10) / 10
}
