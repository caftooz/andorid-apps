package ru.diary.training.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Библиотека готовых программ из `presets.json`: добавляются в «Мои программы» или заменяют существующую. */
object Presets {
    data class Preset(val id: String, val title: String, val group: String, val about: String, val warm: List<String>, val items: List<ProgramItem>)

    val all: List<Preset> by lazy {
        val stream = Presets::class.java.getResourceAsStream("/ru/diary/training/presets.json") ?: return@lazy emptyList()
        val root = stream.use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
        val warm = (root["warm"] as? JsonObject)?.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }.orEmpty()
        root.getValue("presets").jsonArray.map { e ->
            val o = e.jsonObject
            Preset(
                id = o.getValue("id").jsonPrimitive.content,
                title = o.getValue("title").jsonPrimitive.content,
                group = o.getValue("group").jsonPrimitive.content,
                about = o.getValue("about").jsonPrimitive.content,
                warm = warm[o.getValue("warm").jsonPrimitive.content].orEmpty(),
                items = o.getValue("items").jsonArray.map { i ->
                    val io = i.jsonObject
                    ProgramItem(io.getValue("id").jsonPrimitive.content, io.getValue("sets").jsonPrimitive.int, io.getValue("lo").jsonPrimitive.int, io.getValue("hi").jsonPrimitive.int)
                },
            )
        }
    }

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }

    /** Новая программа из готовой: своё id, без буквы (букву раздаёт очередь). */
    fun toProgram(p: Preset, id: String, rot: Boolean): Program = Program(id, p.title, rot, p.warm, p.items)
}
