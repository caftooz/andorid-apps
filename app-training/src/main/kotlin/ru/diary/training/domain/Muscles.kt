package ru.diary.training.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Какие мышцы нагружает упражнение и контуры фигуры для схемы из `body.json`.
 * Контуры — react-body-highlighter (MIT), по фигуре спереди и сзади в координатах 100×200.
 */
object Muscles {
    /** Основные и вспомогательные мышцы упражнения. */
    data class Load(val primary: List<String>, val secondary: List<String>)

    /** Мышца на фигуре: id и многоугольники (x0, y0, x1, y1, …). */
    data class Shape(val id: String, val polygons: List<FloatArray>)

    private val root: JsonObject by lazy {
        val stream = Muscles::class.java.getResourceAsStream("/ru/diary/training/body.json") ?: return@lazy JsonObject(emptyMap())
        stream.use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
    }

    private fun shapes(side: String): List<Shape> =
        ((root["body"] as? JsonObject)?.get(side) as? JsonArray).orEmpty().map { e ->
            val o = e.jsonObject
            Shape(o.getValue("m").jsonPrimitive.content, o.getValue("p").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.float }.toFloatArray() })
        }

    val front: List<Shape> by lazy { shapes("front") }
    val back: List<Shape> by lazy { shapes("back") }

    private val names: Map<String, String> by lazy {
        (root["names"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
    }

    private val loads: Map<String, Load> by lazy {
        (root["ex"] as? JsonObject)?.mapValues { (_, v) ->
            val o = v.jsonObject
            Load(o.getValue("p").jsonArray.map { it.jsonPrimitive.content }, o.getValue("s").jsonArray.map { it.jsonPrimitive.content })
        }.orEmpty()
    }

    /** Нагрузка встроенного упражнения; у своих упражнений схемы нет. */
    fun forEx(id: String): Load? = loads[id]

    fun name(id: String): String = names[id] ?: id

    /** Какая сторона фигуры важнее для упражнения: спереди, если там больше основных мышц. */
    fun mainSideIsFront(load: Load): Boolean {
        val f = front.map { it.id }.toSet()
        val b = back.map { it.id }.toSet()
        return load.primary.count { it in f } >= load.primary.count { it in b }
    }
}
