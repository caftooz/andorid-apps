package ru.diary.training.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.fail

/** Эталон: входы и результаты JS-функций прототипа, сгенерированные `tools/golden/gen.mjs`. */
object Golden {
    const val TODAY = "2026-03-18"

    val root: JsonObject by lazy {
        val s = Golden::class.java.getResourceAsStream("/golden/training-golden.json") ?: error("нет training-golden.json")
        Json.parseToJsonElement(s.use { it.readBytes().toString(Charsets.UTF_8) }).jsonObject
    }

    operator fun get(key: String): JsonElement = root.getValue(key)

    fun sessions(key: String = "sessions"): List<Session> = root.getValue(key).jsonArray.map { Cleaning.cleanSession(it)!! }

    /** План по умолчанию с датой старта из эталона. */
    fun basePlan(start: String = root.getValue("planStart").jsonPrimitive.content): Plan = BuiltinData.defaultPlan().copy(start = start)

    fun baseData(): TrainingData = TrainingData(
        sessions = sessions(), programs = BuiltinData.defaultPrograms(), plan = basePlan(),
        maxes = emptyList(),
    )

    fun dataFrom(o: JsonObject): TrainingData = TrainingData(
        sessions = o.getValue("sessions").jsonArray.map { Cleaning.cleanSession(it)!! },
        programs = o.getValue("programs").jsonArray.map { Cleaning.cleanProgram(it)!! },
        customEx = o.getValue("customEx").jsonArray.map { Cleaning.cleanCustomEx(it)!! },
        plan = Cleaning.cleanPlan(o.getValue("plan")),
        maxes = o.getValue("maxes").jsonArray.map { Cleaning.cleanMax(it)!! },
        wlog = Cleaning.cleanWlog(o.getValue("wlog")),
        templates = o.getValue("templates").jsonArray.map { Cleaning.cleanTemplate(it)!! },
    )

    fun dataJson(d: TrainingData): JsonObject = buildJsonObject {
        put("sessions", JsonArray(d.sessions.map(Transfer::sessionJson)))
        put("programs", JsonArray(d.programs.map { Transfer.programJson(it) }))
        put("customEx", Transfer.buildExport(d, "x").getValue("customExercises"))
        put("plan", Transfer.planJson(d.plan))
        put("maxes", Transfer.buildExport(d, "x").getValue("maxes"))
        put("wlog", Transfer.buildExport(d, "x").getValue("weekLog"))
        put("templates", Transfer.buildExport(d, "x").getValue("templates"))
    }

    fun draftJson(d: ImportDraft): JsonObject {
        val probe = TrainingData(
            sessions = d.list, programs = d.programs, customEx = d.customEx,
            plan = d.plan ?: BuiltinData.defaultPlan(), maxes = d.maxes ?: emptyList(), wlog = d.wlog ?: emptyMap(), templates = d.templates ?: emptyList(),
        )
        val ex = Transfer.buildExport(probe, "x")
        return buildJsonObject {
            put("list", JsonArray(d.list.map(Transfer::sessionJson)))
            put("skipped", JsonPrimitive(d.skipped))
            put("programs", JsonArray(d.programs.map { Transfer.programJson(it) }))
            put("customEx", ex.getValue("customExercises"))
            put("plan", d.plan?.let { Transfer.planJson(it) } ?: JsonNull)
            put("maxes", if (d.maxes != null) ex.getValue("maxes") else JsonNull)
            put("wlog", if (d.wlog != null) ex.getValue("weekLog") else JsonNull)
            put("templates", if (d.templates != null) ex.getValue("templates") else JsonNull)
        }
    }

    /** Случайный id вида `uid()` (время в base36 + случайная часть): у таких записей значения заведомо разные. */
    private fun isGeneratedId(s: String): Boolean = s.length >= 11 && s.all { it.isDigit() || it in 'a'..'z' || it == '-' }

    /** Глубокое сравнение JSON: числа сравниваются как double, ключи объектов не зависят от порядка, `ignore` — имена полей, которые не сравниваем. */
    fun assertJsonEquals(expected: JsonElement, actual: JsonElement, path: String = "$", ignore: Set<String> = emptySet()) {
        when {
            expected is JsonObject && actual is JsonObject -> {
                val ek = expected.keys - ignore
                val ak = actual.keys - ignore
                if (ek != ak) fail("$path: ключи различаются. Ожидалось $ek, получено $ak")
                for (k in ek) assertJsonEquals(expected.getValue(k), actual.getValue(k), "$path.$k", ignore)
            }
            expected is JsonArray && actual is JsonArray -> {
                if (expected.size != actual.size) fail("$path: размер массива. Ожидалось ${expected.size}, получено ${actual.size}")
                expected.forEachIndexed { i, e -> assertJsonEquals(e, actual[i], "$path[$i]", ignore) }
            }
            expected is JsonNull && actual is JsonNull -> Unit
            expected is JsonPrimitive && actual is JsonPrimitive -> {
                if (path.endsWith(".id") && isGeneratedId(expected.content) && isGeneratedId(actual.content)) return
                if (expected.isString != actual.isString) fail("$path: разный тип. Ожидалось $expected, получено $actual")
                if (expected.isString) {
                    if (expected.content != actual.content) fail("$path: Ожидалось «${expected.content}», получено «${actual.content}»")
                } else {
                    val en = expected.content.toDoubleOrNull()
                    val an = actual.content.toDoubleOrNull()
                    if (en != null && an != null) {
                        if (en != an) fail("$path: Ожидалось $en, получено $an")
                    } else if (expected.content != actual.content) fail("$path: Ожидалось $expected, получено $actual")
                }
            }
            else -> fail("$path: разные типы. Ожидалось $expected, получено $actual")
        }
    }
}
