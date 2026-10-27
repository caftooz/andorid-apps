package ru.diary.training.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

object Ids {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    /** Аналог `uid()` прототипа: время в base36 плюс 5 случайных символов. */
    fun uid(now: Long = System.currentTimeMillis()): String =
        java.lang.Long.toString(now, 36) + (1..5).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
}

/** Умолчания по виду упражнения (`DEF_BY_KIND`). */
data class KindDefaults(val sets: Int, val lo: Int, val hi: Int, val step: Double)

data class BuiltinTemplate(val id: String, val name: String, val sub: String)

/** Применение шаблона: новый план и программы, которые заменят одноимённые. */
data class TemplateResult(val plan: Plan, val programs: List<Program>)

/**
 * Встроенные данные прототипа: каталог упражнений, программы A/B/C, шаблоны планов.
 * Читаются из `catalog.json`, который генерируется скриптом `tools/golden/gen.mjs` прямо из `trenirovki.html`,
 * поэтому тексты и числа не переписывались вручную.
 */
object BuiltinData {
    private val root: JsonObject by lazy {
        val stream = BuiltinData::class.java.getResourceAsStream("/ru/diary/training/catalog.json")
            ?: error("catalog.json не найден в ресурсах")
        stream.use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
    }

    private fun strings(key: String): List<String> = root.getValue(key).jsonArray.map { it.jsonPrimitive.content }

    val warmGym: List<String> by lazy { strings("warmGym") }
    val warmGen: List<String> by lazy { strings("warmGen") }

    private val kindDefaults: Map<String, KindDefaults> by lazy {
        root.getValue("defByKind").jsonObject.mapValues { (_, v) ->
            val o = v.jsonObject
            val reps = o.getValue("reps").jsonArray
            KindDefaults(o.getValue("sets").jsonPrimitive.int, reps[0].jsonPrimitive.int, reps[1].jsonPrimitive.int, o.getValue("step").jsonPrimitive.content.toDouble())
        }
    }

    fun defaultsFor(kind: String): KindDefaults = kindDefaults[kind] ?: kindDefaults.getValue("w")

    private fun parseExercises(arr: kotlinx.serialization.json.JsonArray): List<ExDef> = arr.map { e ->
        val o = e.jsonObject
        val reps = o.getValue("reps").jsonArray
        ExDef(
            id = o.getValue("id").jsonPrimitive.content,
            name = o.getValue("name").jsonPrimitive.content,
            kind = o.getValue("kind").jsonPrimitive.content,
            step = o.getValue("step").jsonPrimitive.content.toDouble(),
            sets = o.getValue("sets").jsonPrimitive.int,
            lo = reps[0].jsonPrimitive.int,
            hi = reps[1].jsonPrimitive.int,
            tip = o.getValue("tip").jsonPrimitive.content,
            rest = o["rest"]?.jsonPrimitive?.int ?: 0,
            per = o["per"]?.jsonPrimitive?.content ?: "",
        )
    }

    /** Встроенные упражнения в порядке прототипа. */
    val exercises: List<ExDef> by lazy { parseExercises(root.getValue("exercises").jsonArray) }

    /** Классические упражнения для зала из `gym.json`: жимы, тяги, изоляция. */
    val gymExercises: List<ExDef> by lazy {
        val stream = BuiltinData::class.java.getResourceAsStream("/ru/diary/training/gym.json") ?: return@lazy emptyList()
        stream.use { parseExercises(Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject.getValue("exercises").jsonArray) }
    }

    /** Весь встроенный каталог: упражнения прототипа и классические для зала. */
    val allExercises: List<ExDef> by lazy { exercises + gymExercises }

    /** Подписи «на каждую …» из `PER_LABEL`. */
    val perLabel: Map<String, String> = mapOf(
        "ногу" to "на каждую ногу",
        "руку" to "на каждую руку",
        "сторону" to "на каждую сторону",
    )

    /** Идентификаторы исходных программ (`PROGS0`): их можно «вернуть исходную». */
    val originalProgramIds: Set<String> by lazy { root.getValue("programs0").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet() }

    fun defaultPrograms(): List<Program> = programsOf("defaultPrograms")
    fun girlPrograms(): List<Program> = programsOf("girlPrograms")

    private fun programsOf(key: String): List<Program> =
        root.getValue(key).jsonArray.mapNotNull { Cleaning.cleanProgram(it) }

    private val defaultPlanCache: Plan by lazy { Cleaning.cleanPlan(root.getValue("defaultPlan")) }
    private val blankPlanCache: Plan by lazy { Cleaning.cleanPlan(root.getValue("blankPlan")) }
    private val girlPlanCache: Plan by lazy { Cleaning.cleanPlan(root.getValue("girlPlan")) }

    fun defaultPlan(): Plan = defaultPlanCache
    fun blankPlan(): Plan = blankPlanCache
    fun girlPlan(): Plan = girlPlanCache

    val builtinTemplates: List<BuiltinTemplate> by lazy {
        root.getValue("builtinTemplates").jsonArray.map {
            val o = it.jsonObject
            BuiltinTemplate(o.getValue("id").jsonPrimitive.content, o.getValue("name").jsonPrimitive.content, o.getValue("sub").jsonPrimitive.content)
        }
    }

    /** Встроенный шаблон по id (`vb`, `girl`, `blank`) или `null` для неизвестного. */
    fun builtinTemplate(id: String): TemplateResult? = when (id) {
        "blank" -> TemplateResult(blankPlan(), emptyList())
        "vb" -> TemplateResult(defaultPlan(), emptyList())
        "girl" -> TemplateResult(girlPlan(), girlPrograms())
        else -> null
    }
}

/** Каталог упражнений: встроенные плюс свои. */
class ExerciseCatalog(private val custom: List<CustomEx>) {
    val all: List<ExDef> by lazy {
        BuiltinData.allExercises + custom.map { c ->
            val k = BuiltinData.defaultsFor(c.kind)
            ExDef(c.id, c.name, c.kind, if (c.step > 0) c.step else k.step, k.sets, k.lo, k.hi, c.tip, custom = true)
        }
    }
    private val byId: Map<String, ExDef> by lazy {
        // Как `defOf` прототипа: при совпадении id побеждает первое найденное.
        val m = LinkedHashMap<String, ExDef>()
        for (d in all) m.putIfAbsent(d.id, d)
        m
    }

    fun defOf(id: String): ExDef? = byId[id]
    fun nameOf(id: String): String = defOf(id)?.name ?: id

    fun snapOf(d: ExDef): ExSnap = ExSnap(d.name, d.kind, d.step, d.tip, d.rest, d.per)
}

/** Название вида упражнения (`KIND_NAME`). */
fun kindName(kind: String): String = when (kind) {
    "w" -> "с весом"
    "bw" -> "повторы без веса"
    "time" -> "на время"
    else -> kind
}
