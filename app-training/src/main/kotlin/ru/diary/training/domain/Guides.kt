package ru.diary.training.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Подробные инструкции к домашним занятиям из `guides.json`: общая часть по id занятия и разбор каждого
 * шага или упражнения по названию. Ищутся по названию, поэтому работают и в уже сохранённых планах.
 */
object Guides {
    /** Блок текста инструкции: подзаголовок, абзац или пункт списка (`mark` — «•» или «1.»). */
    sealed interface Block {
        data class Heading(val text: String) : Block
        data class Para(val text: String) : Block
        data class Item(val mark: String, val text: String) : Block
    }

    private val root: JsonObject by lazy {
        val stream = Guides::class.java.getResourceAsStream("/ru/diary/training/guides.json") ?: return@lazy JsonObject(emptyMap())
        stream.use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
    }

    private fun section(key: String): Map<String, String> =
        (root[key] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()

    private val tracks by lazy { section("tracks") }
    private val items by lazy { section("items").mapKeys { norm(it.key) } }

    private fun norm(s: String) = s.trim().lowercase().replace('ё', 'е')

    fun forTrack(id: String): String? = tracks[id]
    fun forItem(name: String): String? = items[norm(name)]

    private val numbered = Regex("""^(\d+)\.\s+(.*)$""")

    /** Разбор простой разметки: «## » — подзаголовок, «- » — пункт, «1. » — шаг, остальное — абзацы. */
    fun parse(text: String): List<Block> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { l ->
        val num = numbered.matchEntire(l)
        when {
            l.startsWith("## ") -> Block.Heading(l.removePrefix("## ").trim())
            l.startsWith("- ") -> Block.Item("•", l.removePrefix("- ").trim())
            num != null -> Block.Item(num.groupValues[1] + ".", num.groupValues[2])
            else -> Block.Para(l)
        }
    }
}
