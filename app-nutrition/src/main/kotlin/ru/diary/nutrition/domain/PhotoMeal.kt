package ru.diary.nutrition.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import ru.diary.core.util.Js

/** Продукт на фото и его оценённый вес. */
data class PhotoItem(val food: Food, val grams: Int)

/** Блюдо, распознанное по фото: название, состав из продуктов базы и комментарий модели. */
data class PhotoMeal(val name: String, val items: List<PhotoItem>, val note: String) {
    val grams: Int get() = items.sumOf { it.grams }
    val total: Nutr get() = items.fold(Nutr.ZERO) { a, x -> a + x.food.per100 * (x.grams / 100.0) }

    /** Запись в дневник одной строкой: «Гречка с курицей (по фото)», граммы и сумма по продуктам. */
    fun toEntry(date: String, time: String, now: Long): Entry {
        val n = total
        return Entry(
            Ids.uid(now), date, time, WEIGHED_PREFIX + "photo", name.trim().ifEmpty { "Блюдо по фото" }.take(120), grams.toDouble(),
            Js.round(n.kcal).toInt(), Js.round(n.p).toInt(), true, fat = Js.round(n.f).toInt(), carbs = Js.round(n.c).toInt(), fiber = Weigh.round1(n.fiber),
        )
    }
}

/**
 * Запрос к модели: определить продукты на фото и оценить вес каждого. Модель выбирает только из нашей базы
 * (список «id — название»), поэтому калории и БЖУ потом считаются по данным USDA, а не придумываются.
 */
object PhotoPrompt {
    fun instruction(): String = buildString {
        appendLine("Ты помощник дневника питания. На фото — еда.")
        appendLine("Определи, из каких продуктов она состоит, и оцени вес каждого в граммах в готовом виде, как он лежит на тарелке.")
        appendLine("Выбирай продукты только из списка ниже (поле food — это id). Если точного продукта нет, возьми самый близкий по составу.")
        appendLine("Учитывай масло и соусы, если они заметны. Не включай посуду, упаковку и несъедобное.")
        appendLine("Если еды на фото нет, верни пустой список items и объясни в note.")
        appendLine("dish — короткое название блюда по-русски. note — одна фраза по-русски о том, в чём ты не уверен.")
        appendLine()
        appendLine("Продукты (id — название):")
        Foods.visible.forEach { appendLine("${it.id} — ${it.name}") }
    }

    /** JSON-схема ответа: food — строго один из id базы, grams — целое. */
    fun schema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("dish") { put("type", "string") }
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("food") { put("type", "string"); putJsonArray("enum") { Foods.visible.forEach { add(it.id) } } }
                        putJsonObject("grams") { put("type", "integer"); put("minimum", 1); put("maximum", 2000) }
                    }
                    putJsonArray("required") { add("food"); add("grams") }
                    put("additionalProperties", false)
                }
            }
            putJsonObject("note") { put("type", "string") }
        }
        putJsonArray("required") { add("dish"); add("items"); add("note") }
        put("additionalProperties", false)
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Разбор ответа модели; неизвестные продукты и неверный вес пропускаются, одинаковые продукты складываются. */
    fun parse(text: String): PhotoMeal? {
        val o = runCatching { json.parseToJsonElement(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).jsonObject }.getOrNull() ?: return null
        val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
            val it = e as? JsonObject ?: return@mapNotNull null
            val food = (it["food"] as? JsonPrimitive)?.contentOrNull?.let { id -> Foods.byId[id] } ?: return@mapNotNull null
            val g = (it["grams"] as? JsonPrimitive)?.let { p -> p.intOrNull ?: p.contentOrNull?.toDoubleOrNull()?.toInt() }?.takeIf { g -> g in 1..2000 } ?: return@mapNotNull null
            PhotoItem(food, g)
        }.groupBy { it.food.id }.map { (_, l) -> PhotoItem(l.first().food, l.sumOf { it.grams }) }
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        return PhotoMeal(s("dish").take(120), items, s("note").take(300))
    }
}
