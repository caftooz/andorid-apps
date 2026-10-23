package ru.diary.core.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.floor

/**
 * Помощники, повторяющие поведение JS из веб-прототипа: округление, `parseFloat`, унарный плюс,
 * `String(v)`. Нужны, чтобы импорт JSON из веб-версии давал те же значения, что и в браузере.
 */
object Js {
    /** `Math.round`: половина округляется вверх (к +∞). */
    fun round(x: Double): Double = floor(x + 0.5)

    fun clamp(v: Double, lo: Double, hi: Double): Double = maxOf(lo, minOf(hi, v))
    fun clamp(v: Int, lo: Int, hi: Int): Int = maxOf(lo, minOf(hi, v))

    private val FLOAT_PREFIX = Regex("""^[+-]?(Infinity|\d+\.?\d*(?:[eE][+-]?\d+)?|\.\d+(?:[eE][+-]?\d+)?)""")

    /** `parseFloat(s)`: число в начале строки или NaN. */
    fun parseFloat(s: String): Double {
        val m = FLOAT_PREFIX.find(s.trimStart(*WHITESPACE)) ?: return Double.NaN
        val t = m.value
        return when {
            t.endsWith("Infinity") -> if (t.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            else -> t.toDoubleOrNull() ?: Double.NaN
        }
    }

    private val WHITESPACE = charArrayOf(' ', '\t', '\n', '\r', '\u000B', '\u000C', ' ', '\uFEFF', ' ', ' ')

    private val NUMBER_STRING = Regex("""^[+-]?(\d+\.?\d*(?:[eE][+-]?\d+)?|\.\d+(?:[eE][+-]?\d+)?)$""")

    /** Унарный плюс над строкой: `+"  12 "` = 12, `+""` = 0, `+"12,5"` = NaN. */
    fun toNumber(s: String): Double {
        val t = s.trim(*WHITESPACE)
        if (t.isEmpty()) return 0.0
        if (t == "Infinity" || t == "+Infinity") return Double.POSITIVE_INFINITY
        if (t == "-Infinity") return Double.NEGATIVE_INFINITY
        return if (NUMBER_STRING.matches(t)) t.toDouble() else Double.NaN
    }

    /**
     * Унарный плюс над значением JSON (`+x`). Отсутствующее поле (Kotlin `null`) даёт NaN,
     * явный `null` даёт 0, как в JS.
     */
    fun toNumber(e: JsonElement?): Double = when (e) {
        null -> Double.NaN
        is JsonNull -> 0.0
        is JsonPrimitive -> when {
            e.isString -> toNumber(e.content)
            e.content == "true" -> 1.0
            e.content == "false" -> 0.0
            else -> e.content.toDoubleOrNull() ?: Double.NaN
        }
        is JsonArray -> when (e.size) {
            0 -> 0.0
            1 -> toNumber(e[0])
            else -> Double.NaN
        }
        is JsonObject -> Double.NaN
    }

    /** `+x || fallback`: NaN и 0 считаются ложными. */
    fun numOr(e: JsonElement?, fallback: Double): Double {
        val n = toNumber(e)
        return if (n.isNaN() || n == 0.0) fallback else n
    }

    /** Строка в формате JS-числа: `5`, `2.5`, без «.0». */
    fun numToString(d: Double): String = when {
        d.isNaN() -> "NaN"
        d == Double.POSITIVE_INFINITY -> "Infinity"
        d == Double.NEGATIVE_INFINITY -> "-Infinity"
        d == floor(d) && abs(d) < 1e15 -> d.toLong().toString()
        else -> d.toString()
    }

    /** `String(v == null ? '' : v)`. */
    fun str(e: JsonElement?): String = when (e) {
        null, is JsonNull -> ""
        is JsonPrimitive -> if (e.isString) e.content else if (e.content == "true" || e.content == "false") e.content else
            e.content.toDoubleOrNull()?.let(::numToString) ?: e.content
        is JsonArray -> e.joinToString(",") { if (it is JsonNull) "" else str(it) }
        is JsonObject -> "[object Object]"
    }

    /** `String(v == null ? '' : v).slice(0, n)`. */
    fun str(e: JsonElement?, n: Int): String = str(e).take(n)

    /** Истинность значения как в JS: пустая строка, 0, NaN, null и отсутствие значения ложны. */
    fun truthy(e: JsonElement?): Boolean = when (e) {
        null, is JsonNull -> false
        is JsonPrimitive -> when {
            e.isString -> e.content.isNotEmpty()
            e.content == "true" -> true
            e.content == "false" -> false
            else -> e.content.toDoubleOrNull()?.let { it != 0.0 && !it.isNaN() } ?: true
        }
        else -> true
    }

    /** `parseNum` прототипа: неотрицательное число, округлённое до десятых, не больше 1000. */
    fun parseNum(e: JsonElement?): Double = parseNum(if (e == null || e is JsonNull) "" else str(e))

    fun parseNum(v: String): Double {
        val n = parseFloat(v.replaceFirst(',', '.'))
        if (n.isNaN() || n.isInfinite() || n < 0) return 0.0
        return minOf(1000.0, round(n * 10) / 10)
    }

    /** `fmt` прототипа: одна цифра после запятой, десятичный разделитель — запятая. */
    fun fmt(n: Double): String = numToString(round(n * 10) / 10).replace('.', ',')

    /** Склонение числительных: 1 подход, 2 подхода, 5 подходов. */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val m = n % 100
        val k = n % 10
        return when {
            m in 11..19 -> many
            k == 1 -> one
            k in 2..4 -> few
            else -> many
        }
    }
}
