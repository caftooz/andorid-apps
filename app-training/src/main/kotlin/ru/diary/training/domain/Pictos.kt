package ru.diary.training.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.cos
import kotlin.math.sin

/**
 * Пиктограммы «как делать» из `pictos.json`: 1–2 позы схематичной фигуры, снаряды и сустав для стрелки.
 * Фигура задаётся углами суставов; здесь считаются координаты суставов в кадре 100×100 (пол — y = 96).
 * Та же математика, что в скрипте превью `tools/pictos`.
 */
object Pictos {
    data class Pt(val x: Float, val y: Float) {
        operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
        operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    }

    /** Снаряд: вид (`bench`, `plate`, `cable`…), числа и привязка к суставу. */
    data class Item(val kind: String, val p: List<Float>, val at: String?, val to: String?, val d: Pt, val r: Float?, val a: Float, val l: Float, val w: Float?, val b: Pt?, val c1: Pt?, val c2: Pt?)

    data class Picto(val id: String, val poses: List<JsonObject>, val eq: List<Item>, val eq2: List<Item>, val arrows: List<String>)

    /** Позы одного кадра: суставы и отрезки (дальние конечности, корпус, ноги, руки). */
    data class Frame(val j: Map<String, Pt>, val far: List<Pair<String, String>>, val torso: List<Pair<String, String>>, val legs: List<Pair<String, String>>, val arms: List<Pair<String, String>>)

    private const val TOR = 28f; private const val NECK = 3f; private const val HEADR = 5.5f
    private const val UA = 14f; private const val FA = 13f; private const val TH = 19f; private const val SH = 19f; private const val FT = 6f
    private const val SHW = 7f; private const val HIPW = 4f

    private val all: Map<String, Picto> by lazy {
        val stream = Pictos::class.java.getResourceAsStream("/ru/diary/training/pictos.json") ?: return@lazy emptyMap()
        val arr = stream.use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonArray }
        arr.associate { e ->
            val o = e.jsonObject
            val eq = items(o["eq"])
            val id = o.getValue("id").jsonPrimitive.content
            id to Picto(id, o.getValue("poses").jsonArray.map { it.jsonObject }, eq, if (o.containsKey("eq2")) items(o["eq2"]) else eq,
                (o["k"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: listOf("hand"))
        }
    }

    fun forEx(id: String): Picto? = all[id]

    private fun nums(e: Any?): List<Float> = (e as? JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull?.toFloat() }.orEmpty()
    private fun pt(e: Any?): Pt? = nums(e).takeIf { it.size >= 2 }?.let { Pt(it[0], it[1]) }
    private fun str(e: Any?): String? = (e as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun num(e: Any?): Float? = (e as? JsonPrimitive)?.doubleOrNull?.toFloat()

    private fun items(e: Any?): List<Item> = (e as? JsonArray).orEmpty().map { x ->
        val o = x.jsonObject
        Item(
            kind = o.getValue("k").jsonPrimitive.content, p = nums(o["p"]),
            at = str(o["at"]), to = str(o["to"]), d = pt(o["d"]) ?: Pt(0f, 0f), r = num(o["r"]), a = num(o["a"]) ?: 0f, l = num(o["l"]) ?: 8f,
            w = num(o["w"]), b = pt(o["b"]), c1 = pt(o["c1"]), c2 = pt(o["c2"]),
        )
    }

    private fun rad(a: Float) = Math.toRadians(a.toDouble())
    /** Вектор длины `len` под углом `a` от направления «вниз», плюс — вперёд (вправо). */
    private fun down(a: Float, len: Float) = Pt((sin(rad(a)) * len).toFloat(), (cos(rad(a)) * len).toFloat())
    /** Вектор длины `len` под углом `a` от направления «вверх», плюс — вперёд. */
    private fun up(a: Float, len: Float) = Pt((sin(rad(a)) * len).toFloat(), (-cos(rad(a)) * len).toFloat())
    private fun rot(v: Pt, deg: Float): Pt {
        val c = cos(rad(deg)).toFloat(); val s = sin(rad(deg)).toFloat()
        return Pt(v.x * c - v.y * s, v.x * s + v.y * c)
    }

    private fun pair(o: JsonObject, key: String, def: List<Float>): List<Float> = nums(o[key]).takeIf { it.size == 2 } ?: def

    /** Суставы позы: боковой вид (по умолчанию) или фронтальный (`"v": "front"`). */
    fun frame(p: JsonObject): Frame = if (str(p["v"]) == "front") front(p) else side(p)

    private fun side(p: JsonObject): Frame {
        val t = num(p["t"]) ?: 0f
        val a = pair(p, "a", listOf(0f, 0f)); val a2 = pair(p, "a2", a)
        val l = pair(p, "l", listOf(0f, 0f)); val l2 = pair(p, "l2", l)
        val sa = num(p["sa"]) ?: 1f; val sa2 = num(p["sa2"]) ?: sa
        val h = pt(p["A"])?.let { it - down(l[1], SH) - down(l[0], TH) } ?: pt(p["H"]) ?: Pt(50f, 55f)
        val s = h + up(t, TOR + (num(p["sy"]) ?: 0f))
        val j = HashMap<String, Pt>()
        j["hip"] = h; j["sh"] = s; j["head"] = s + up(t + (num(p["hd"]) ?: 0f), NECK + HEADR)
        j["el"] = s + down(a[0], UA * sa); j["hand"] = j.getValue("el") + down(a[1], FA * sa)
        j["el2"] = s + down(a2[0], UA * sa2); j["hand2"] = j.getValue("el2") + down(a2[1], FA * sa2)
        val f = num(p["f"]); val f2 = num(p["f2"]) ?: f
        for ((suf, leg, ff) in listOf(Triple("", l, f), Triple("2", l2, f2))) {
            val k = h + down(leg[0], TH); val an = k + down(leg[1], SH)
            j["knee$suf"] = k; j["ankle$suf"] = an; j["toe$suf"] = an + down(ff ?: (leg[1] + 90f), FT)
        }
        return Frame(
            j, far = listOf("sh" to "el2", "el2" to "hand2", "hip" to "knee2", "knee2" to "ankle2", "ankle2" to "toe2"),
            torso = listOf("hip" to "sh"), legs = listOf("hip" to "knee", "knee" to "ankle", "ankle" to "toe"), arms = listOf("sh" to "el", "el" to "hand"),
        )
    }

    private fun front(p: JsonObject): Frame {
        val r = num(p["r"]) ?: 0f
        val a = pair(p, "a", listOf(0f, 0f)); val a2 = pair(p, "a2", a)
        val l = pair(p, "l", listOf(5f, 0f)); val l2 = pair(p, "l2", l)
        val sa = num(p["sa"]) ?: 1f; val sa2 = num(p["sa2"]) ?: sa
        val sy = num(p["sy"]) ?: 0f
        val loc = HashMap<String, Pt>()
        loc["hip"] = Pt(0f, 0f); loc["sh"] = Pt(0f, -TOR - sy); loc["head"] = Pt(0f, -TOR - NECK - HEADR)
        for ((suf, sgn, arm, s, leg) in listOf(Five("", 1f, a, sa, l), Five("2", -1f, a2, sa2, l2))) {
            val shp = Pt(sgn * SHW, -TOR + 1.5f - sy)
            val e1 = down(arm[0], UA * s); val el = shp + Pt(sgn * e1.x, e1.y)
            val e2 = down(arm[1], FA * s); val hd = el + Pt(sgn * e2.x, e2.y)
            val hp = Pt(sgn * HIPW, 0f)
            val k1 = down(leg[0], TH); val kn = hp + Pt(sgn * k1.x, k1.y)
            val k2 = down(leg[1], SH); val an = kn + Pt(sgn * k2.x, k2.y)
            loc["shp$suf"] = shp; loc["el$suf"] = el; loc["hand$suf"] = hd; loc["hp$suf"] = hp; loc["knee$suf"] = kn; loc["ankle$suf"] = an
        }
        val h = pt(p["A"])?.let { it - rot(loc.getValue("ankle"), r) } ?: pt(p["H"]) ?: Pt(50f, 55f)
        val j = loc.mapValues { (_, v) -> h + rot(v, r) }.toMutableMap()
        j["toe"] = j.getValue("ankle"); j["toe2"] = j.getValue("ankle2")
        return Frame(
            j, far = emptyList(), torso = listOf("shp" to "shp2", "hp" to "hp2", "hip" to "sh"),
            legs = listOf("hp" to "knee", "knee" to "ankle", "hp2" to "knee2", "knee2" to "ankle2"),
            arms = listOf("shp" to "el", "el" to "hand", "shp2" to "el2", "el2" to "hand2"),
        )
    }

    private data class Five(val suf: String, val sgn: Float, val arm: List<Float>, val s: Float, val leg: List<Float>)
}
