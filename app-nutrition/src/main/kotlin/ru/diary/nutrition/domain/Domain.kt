package ru.diary.nutrition.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import ru.diary.core.date.Dates
import ru.diary.core.util.Js
import kotlin.random.Random

/** Цели из прототипа: белок в граммах (от и до) и ориентир по калориям. */
object Goals {
    var proteinMin = 105
    var proteinMax = 130
    var kcal = 2500
}

val CATS = listOf("all" to "Все", "breakfast" to "Завтраки", "main" to "Обед и ужин", "side" to "Гарниры", "snack" to "Перекусы")
val CAT_NAME = mapOf("breakfast" to "Завтрак", "main" to "Обед и ужин", "side" to "Гарнир", "snack" to "Перекус")
val PORTIONS = listOf(0.75 to "Малая", 1.0 to "Обычная", 1.5 to "Большая")

/** Блюдо: встроенное (с рецептом и КБЖУ) или своё (`custom`, только ккал и белок). */
data class Dish(
    val id: String,
    val name: String,
    val cat: String,
    val kcal: Int,
    val p: Int,
    val f: Int?,
    val c: Int?,
    val tags: List<String>,
    val time: String?,
    val batch: String?,
    val ing: List<String>,
    val steps: List<String>,
    val custom: Boolean = false,
    val notes: String = "",
)

data class CustomDish(val id: String, val name: String, val cat: String, val kcal: Int, val p: Int, val notes: String)

/**
 * Запись приёма пищи. `plan = false` — приём вне плана.
 * Для продукта по штрих-коду `dish` = «bc:<код>», а `portion` — съеденные граммы.
 * `fat` и `carbs` известны только для продуктов; для блюд они считаются по каталогу.
 */
data class Entry(
    val id: String,
    val date: String,
    val time: String,
    val dish: String?,
    val name: String,
    val portion: Double,
    val kcal: Int,
    val protein: Int,
    val plan: Boolean,
    val fat: Int? = null,
    val carbs: Int? = null,
) {
    val isProduct: Boolean get() = dish?.startsWith(PRODUCT_PREFIX) == true
}

const val PRODUCT_PREFIX = "bc:"

/**
 * Продукт со штрих-кодом, КБЖУ на 100 г. `source`: «off» — найден в Open Food Facts, «user» — введён вручную.
 * `packG` — вес упаковки в граммах, если известен.
 */
data class Product(
    val code: String,
    val name: String,
    val brand: String,
    val kcal: Double,
    val p: Double,
    val f: Double,
    val c: Double,
    val packG: Int?,
    val source: String,
    val updated: Long,
)

data class NutritionData(
    val log: List<Entry> = emptyList(),
    val custom: List<CustomDish> = emptyList(),
    val products: List<Product> = emptyList(),
)

/** Работа с продуктами: проверка штрих-кода, пересчёт на граммы, ручной ввод. */
object Products {
    /** EAN-8, UPC-A, EAN-13 и ITF-14: только цифры нужной длины. */
    fun normalize(raw: String): String? = raw.filter { !it.isWhitespace() }.takeIf { s -> s.length in setOf(8, 12, 13, 14) && s.all { it.isDigit() } }

    private fun dec(v: String): Double? = v.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }

    /** Продукт из полей формы; `null`, если нет названия или калорийности. */
    fun manual(code: String, name: String, brand: String, kcal: String, p: String, f: String, c: String, pack: String, now: Long): Product? {
        val n = name.trim().take(120)
        val k = dec(kcal) ?: return null
        if (n.isEmpty() || k > 1000) return null
        return Product(
            code, n, brand.trim().take(80), k, (dec(p) ?: 0.0).coerceAtMost(100.0), (dec(f) ?: 0.0).coerceAtMost(100.0),
            (dec(c) ?: 0.0).coerceAtMost(100.0), pack.trim().toIntOrNull()?.takeIf { it in 1..20000 }, "user", now,
        )
    }

    /** Запись в дневник: КБЖУ пересчитываются с 100 г на съеденные граммы. */
    fun eat(prod: Product, grams: Int, date: String, time: String, now: Long): Entry {
        val k = grams / 100.0
        return Entry(
            Ids.uid(now), date, time, PRODUCT_PREFIX + prod.code, prod.name, grams.toDouble(),
            Js.round(prod.kcal * k).toInt(), Js.round(prod.p * k).toInt(), true,
            fat = Js.round(prod.f * k).toInt(), carbs = Js.round(prod.c * k).toInt(),
        )
    }

    fun title(prod: Product): String = if (prod.brand.isEmpty()) prod.name else "${prod.name} · ${prod.brand}"
}

/** Подпись количества: для продукта — граммы, для блюда — размер порции. */
fun amountLabel(e: Entry): String = if (e.isProduct) "${e.portion.toInt()} г" else portionLabel(e.portion)

fun portionLabel(p: Double): String = PORTIONS.firstOrNull { it.first == p }?.second?.lowercase() ?: "×" + Js.numToString(p)

object Ids {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
    fun uid(now: Long = System.currentTimeMillis()): String =
        java.lang.Long.toString(now, 36) + (1..5).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
}

/** Встроенный каталог блюд из `catalog.json` (генерируется из `raciona.html`). */
object Catalog {
    private val root: JsonObject by lazy {
        val s = Catalog::class.java.getResourceAsStream("/ru/diary/nutrition/catalog.json") ?: error("catalog.json не найден")
        s.use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
    }

    init {
        Goals.proteinMin = root.getValue("goalProteinMin").jsonPrimitive.content.toInt()
        Goals.proteinMax = root.getValue("goalProteinMax").jsonPrimitive.content.toInt()
        Goals.kcal = root.getValue("goalKcal").jsonPrimitive.content.toInt()
    }

    private fun JsonElement?.intOrNull(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.toInt()
    private fun JsonElement?.strOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonElement?.strings(): List<String> = (this as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

    val builtin: List<Dish> by lazy {
        root.getValue("dishes").jsonArray.map { e ->
            val o = e.jsonObject
            Dish(
                id = o.getValue("id").jsonPrimitive.content, name = o.getValue("name").jsonPrimitive.content, cat = o.getValue("cat").jsonPrimitive.content,
                kcal = o["kcal"].intOrNull() ?: 0, p = o["p"].intOrNull() ?: 0, f = o["f"].intOrNull(), c = o["c"].intOrNull(),
                tags = o["tags"].strings(), time = o["time"].strOrNull(), batch = o["batch"].strOrNull(),
                ing = o["ing"].strings(), steps = o["steps"].strings(),
            )
        }
    }

    fun all(custom: List<CustomDish>): List<Dish> = builtin + custom.map {
        Dish(it.id, it.name, it.cat, it.kcal, it.p, null, null, emptyList(), null, null, emptyList(), emptyList(), custom = true, notes = it.notes)
    }
}

/** Агрегаты за день для интерфейса и для провайдера данных. */
data class DayTotals(val kcal: Int, val protein: Int, val fat: Int, val carbs: Int, val meals: Int)

data class WeekStats(
    val days: List<Pair<String, Int>>,
    val loggedDays: Int,
    val avgProtein: Int,
    val total: Int,
    val outOfPlanShare: Int,
    val fish: Int,
    val variety: Int,
    val notes: List<String>,
)

object NutritionLogic {
    fun num(v: String): Int {
        val n = Js.parseFloat(v.replaceFirst(',', '.'))
        return if (n.isNaN() || n.isInfinite() || n <= 0) 0 else Js.round(n).toInt()
    }

    fun num(e: JsonElement?): Int = num(if (e == null || e is JsonNull) "" else Js.str(e))

    fun entriesOn(data: NutritionData, d: String): List<Entry> = data.log.filter { it.date == d }.sortedBy { it.time }

    fun lastEaten(data: NutritionData, id: String): String? = data.log.filter { it.dish == id }.maxOfOrNull { it.date }

    /** Приём блюда: ккал и белок умножаются на порцию и округляются. */
    fun eat(dish: Dish, date: String, time: String, portion: Double, now: Long): Entry = Entry(
        Ids.uid(now), date, time, dish.id, dish.name, portion,
        Js.round(dish.kcal * portion).toInt(), Js.round(dish.p * portion).toInt(), true,
    )

    fun outOfPlan(name: String, kcal: String, protein: String, date: String, time: String, now: Long): Entry = Entry(
        Ids.uid(now), date, time, null, name.trim().take(120).ifEmpty { "Вне плана" }, 1.0, num(kcal), num(protein), false,
    )

    fun newDish(name: String, cat: String, kcal: String, protein: String, notes: String): CustomDish? {
        val n = name.trim()
        if (n.isEmpty()) return null
        return CustomDish("c-" + Ids.uid(), n.take(120), if (cat in CAT_NAME) cat else "main", num(kcal), num(protein), notes.take(4000))
    }

    /**
     * Порядок списка блюд: «давно не ел сверху» (по давности, затем по алфавиту) или по алфавиту.
     * Алфавит — как `localeCompare('ru')`: без учёта регистра.
     */
    fun ordered(data: NutritionData, today: String, byAge: Boolean): List<Dish> {
        val all = Catalog.all(data.custom)
        val collator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("ru")).apply { strength = java.text.Collator.TERTIARY }
        return all.map { d ->
            val last = lastEaten(data, d.id)
            Triple(d, if (last == null) 1_000_000 else Dates.daysBetween(last, today), d.name)
        }.sortedWith { a, b ->
            if (!byAge) collator.compare(a.third, b.third)
            else (b.second - a.second).takeIf { it != 0 } ?: collator.compare(a.third, b.third)
        }.map { it.first }
    }

    /** Жиры и углеводы: у продуктов — из записи, у блюд — по каталогу; для записей вне плана и своих блюд они неизвестны (0). */
    fun totals(data: NutritionData, date: String): DayTotals {
        val all = Catalog.all(data.custom).associateBy { it.id }
        var k = 0; var p = 0; var f = 0.0; var c = 0.0
        val es = entriesOn(data, date)
        for (e in es) {
            k += e.kcal; p += e.protein
            if (e.fat != null || e.carbs != null) { f += e.fat ?: 0; c += e.carbs ?: 0; continue }
            val d = e.dish?.let { all[it] }
            if (d != null) { f += (d.f ?: 0) * e.portion; c += (d.c ?: 0) * e.portion }
        }
        return DayTotals(k, p, Js.round(f).toInt(), Js.round(c).toInt(), es.size)
    }

    fun week(data: NutritionData, today: String): WeekStats {
        val days = (6 downTo 0).map { Dates.addDays(today, -it) }
        val es = data.log.filter { it.date >= days.first() && it.date <= today }
        var logged = 0; var pTotal = 0
        val perDay = days.map { d ->
            val day = entriesOn(data, d)
            val p = day.sumOf { it.protein }
            if (day.isNotEmpty()) { logged++; pTotal += p }
            d to p
        }
        val total = es.size
        val oop = es.count { !it.plan }
        val share = if (total > 0) Js.round(oop * 100.0 / total).toInt() else 0
        val all = Catalog.all(data.custom).associateBy { it.id }
        val fish = es.count { e -> e.dish?.let { all[it] }?.tags?.contains("fish") == true }
        val variety = es.mapNotNull { it.dish }.toSet().size
        val avg = if (logged > 0) Js.round(pTotal.toDouble() / logged).toInt() else 0
        val notes = ArrayList<String>()
        if (total < 5) notes.add("Пока мало записей для выводов. Достаточно отмечать то, что вы едите, без точности до грамма.")
        else {
            notes.add(if (share <= 20) "Вне плана $share%: в пределах 20%, всё в порядке." else "Вне плана $share%. Это нормально: следующий приём пищи просто идёт по плану, без компенсаций.")
            if (logged >= 3 && avg < Goals.proteinMin) notes.add("Белка в среднем меньше ${Goals.proteinMin} г. Проще всего добавить творог, яйца или кефир.")
            if (fish < 2) notes.add("Рыбы на этой неделе меньше двух раз.")
        }
        return WeekStats(perDay, logged, avg, total, share, fish, variety, notes)
    }

    /** Три блюда, которые дольше всего не ели (завтраки и обед/ужин). */
    fun stale(data: NutritionData, today: String): List<Pair<Dish, Int?>> =
        Catalog.all(data.custom).filter { it.cat == "main" || it.cat == "breakfast" }.map { d ->
            val l = lastEaten(data, d.id)
            Triple(d, l?.let { Dates.daysBetween(it, today) }, if (l == null) 1_000_000 else Dates.daysBetween(l, today))
        }.sortedByDescending { it.third }.take(3).map { it.first to it.second }
}

/** Экспорт и импорт JSON, совместимые с веб-версией «Рациона» (`version: 1`). */
object NutritionTransfer {
    const val APP = "dnevnik-raciona"
    const val VERSION = 1
    private const val NOTE = "log: записи о приёмах пищи (date, time, dish, name, portion, kcal, protein, plan; у продуктов ещё fat, carbs, а portion — граммы). " +
        "customDishes: ваши блюда. products: продукты по штрих-коду, КБЖУ на 100 г. " +
        "catalog: все блюда с рецептами, КБЖУ на порцию приблизительное."

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private fun str(v: String?): JsonElement = v?.let { JsonPrimitive(it) } ?: JsonNull
    private fun int(v: Int?): JsonElement = v?.let { JsonPrimitive(it) } ?: JsonNull
    private fun portion(p: Double): JsonPrimitive = if (p == kotlin.math.floor(p)) JsonPrimitive(p.toLong()) else JsonPrimitive(p)

    fun entryJson(e: Entry): JsonObject = buildJsonObject {
        put("id", e.id); put("date", e.date); put("time", e.time); put("dish", str(e.dish)); put("name", e.name)
        put("portion", portion(e.portion)); put("kcal", JsonPrimitive(e.kcal)); put("protein", JsonPrimitive(e.protein)); put("plan", e.plan)
        // Новые поля пишутся только когда есть: записи блюд остаются в точности как в веб-версии.
        e.fat?.let { put("fat", JsonPrimitive(it)) }
        e.carbs?.let { put("carbs", JsonPrimitive(it)) }
    }

    fun productJson(p: Product): JsonObject = buildJsonObject {
        put("code", p.code); put("name", p.name); put("brand", p.brand)
        put("kcal100", JsonPrimitive(p.kcal)); put("protein100", JsonPrimitive(p.p)); put("fat100", JsonPrimitive(p.f)); put("carbs100", JsonPrimitive(p.c))
        put("packGrams", int(p.packG)); put("source", p.source); put("updated", JsonPrimitive(p.updated))
    }

    fun customJson(c: CustomDish): JsonObject = buildJsonObject {
        put("id", c.id); put("name", c.name); put("cat", c.cat); put("kcal", JsonPrimitive(c.kcal)); put("p", JsonPrimitive(c.p)); put("notes", c.notes)
    }

    fun buildExport(d: NutritionData, exportedAt: String): JsonObject = buildJsonObject {
        put("app", APP); put("version", JsonPrimitive(VERSION)); put("exportedAt", exportedAt)
        putJsonObject("goals") {
            put("proteinGramsMin", JsonPrimitive(Goals.proteinMin)); put("proteinGramsMax", JsonPrimitive(Goals.proteinMax)); put("kcalApprox", JsonPrimitive(Goals.kcal))
        }
        put("note", NOTE)
        put("log", JsonArray(d.log.sortedBy { it.date + it.time }.map(::entryJson)))
        put("customDishes", JsonArray(d.custom.map(::customJson)))
        if (d.products.isNotEmpty()) put("products", JsonArray(d.products.map(::productJson)))
        put("catalog", buildJsonArray {
            Catalog.all(d.custom).forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("name", x.name); put("category", x.cat); put("kcal", JsonPrimitive(x.kcal)); put("protein", JsonPrimitive(x.p))
                    put("fat", int(x.f)); put("carbs", int(x.c)); put("time", str(x.time)); put("batch", str(x.batch))
                    put("ingredients", JsonArray(x.ing.map { JsonPrimitive(it) })); put("steps", JsonArray(x.steps.map { JsonPrimitive(it) })); put("notes", x.notes)
                })
            }
        })
    }

    fun exportText(d: NutritionData, exportedAt: String): String = pretty.encodeToString(JsonElement.serializer(), buildExport(d, exportedAt))

    private fun obj(e: JsonElement?) = e as? JsonObject
    private val TIME_RE = Regex("""\d{2}:\d{2}""")

    fun cleanEntry(raw: JsonElement?): Entry? {
        val e = obj(raw) ?: return null
        val date = Js.str(if (Js.truthy(e["date"])) e["date"] else null)
        if (!Dates.isDate(date)) return null
        val timeStr = Js.str(e["time"])
        val portion = Js.toNumber(e["portion"]).let { if (it > 0) it else 1.0 }
        return Entry(
            id = if (Js.truthy(e["id"])) Js.str(e["id"]) else Ids.uid(), date = date, time = if (TIME_RE.matches(timeStr)) timeStr else "12:00",
            dish = if (Js.truthy(e["dish"])) Js.str(e["dish"]) else null,
            name = if (Js.truthy(e["name"])) Js.str(e["name"], 120) else "Без названия", portion = portion,
            kcal = NutritionLogic.num(e["kcal"]), protein = NutritionLogic.num(e["protein"]),
            plan = (e["plan"] as? JsonPrimitive)?.let { it.content != "false" } ?: true,
            fat = e["fat"]?.takeIf { it !is JsonNull }?.let { NutritionLogic.num(it) },
            carbs = e["carbs"]?.takeIf { it !is JsonNull }?.let { NutritionLogic.num(it) },
        )
    }

    private fun dbl(e: JsonElement?): Double = Js.toNumber(e).takeIf { it.isFinite() && it >= 0 } ?: 0.0

    fun cleanProduct(raw: JsonElement?): Product? {
        val o = obj(raw) ?: return null
        val code = Products.normalize(Js.str(o["code"])) ?: return null
        if (!Js.truthy(o["name"])) return null
        return Product(
            code, Js.str(o["name"], 120), if (Js.truthy(o["brand"])) Js.str(o["brand"], 80) else "",
            dbl(o["kcal100"]).coerceAtMost(1000.0), dbl(o["protein100"]).coerceAtMost(100.0), dbl(o["fat100"]).coerceAtMost(100.0), dbl(o["carbs100"]).coerceAtMost(100.0),
            Js.toNumber(o["packGrams"]).takeIf { it.isFinite() && it >= 1 && it <= 20000 }?.toInt(),
            if (Js.str(o["source"]) == "off") "off" else "user", Js.toNumber(o["updated"]).takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L,
        )
    }

    fun cleanDish(raw: JsonElement?): CustomDish? {
        val c = obj(raw) ?: return null
        if (!Js.truthy(c["id"]) || !Js.truthy(c["name"])) return null
        val cat = Js.str(c["cat"])
        return CustomDish(
            Js.str(c["id"]), Js.str(c["name"], 120), if (cat in CAT_NAME) cat else "main", NutritionLogic.num(c["kcal"]),
            NutritionLogic.num(if (c["p"] != null && c["p"] !is JsonNull) c["p"] else c["protein"]), Js.str(if (Js.truthy(c["notes"])) c["notes"] else null, 4000),
        )
    }

    data class Draft(val log: List<Entry>, val custom: List<CustomDish>, val skipped: Int, val products: List<Product> = emptyList())

    enum class Mode { Replace, Merge }

    /** Разбор файла; `null`, если это не JSON дневника рациона. Повреждённые записи пропускаются и считаются. */
    fun parseImport(text: String): Draft? {
        val o = try { Json.parseToJsonElement(text) } catch (e: Exception) { return null }
        val raw = (if (o is JsonArray) o else obj(o)?.get("log") as? JsonArray) ?: return null
        val log = raw.mapNotNull { cleanEntry(it) }
        val custom = ((obj(o)?.get("customDishes") as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { cleanDish(it) }
        val products = ((obj(o)?.get("products") as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { cleanProduct(it) }
        return Draft(log, custom, raw.size - log.size, products)
    }

    fun applyImport(cur: NutritionData, d: Draft, mode: Mode): NutritionData {
        if (mode == Mode.Replace) return NutritionData(d.log, d.custom, d.products)
        val have = cur.log.mapTo(HashSet()) { it.id }
        val hc = cur.custom.mapTo(HashSet()) { it.id }
        val hp = cur.products.mapTo(HashSet()) { it.code }
        return NutritionData(cur.log + d.log.filter { it.id !in have }, cur.custom + d.custom.filter { it.id !in hc }, cur.products + d.products.filter { it.code !in hp })
    }
}
