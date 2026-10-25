package ru.diary.nutrition.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.diary.core.util.Js
import kotlin.math.roundToInt

/** Пищевая ценность: ккал, белки, жиры, углеводы, клетчатка и сахар (граммы). */
data class Nutr(val kcal: Double, val p: Double, val f: Double, val c: Double, val fiber: Double, val sugar: Double = 0.0) {
    operator fun times(k: Double) = Nutr(kcal * k, p * k, f * k, c * k, fiber * k, sugar * k)
    operator fun plus(o: Nutr) = Nutr(kcal + o.kcal, p + o.p, f + o.f, c + o.c, fiber + o.fiber, sugar + o.sugar)

    companion object { val ZERO = Nutr(0.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
}

/** Отдельный продукт для взвешивания, пищевая ценность на 100 г. `src` — «usda:<id>» или «label» (российская этикетка). */
data class Food(val id: String, val name: String, val cat: String, val per100: Nutr, val src: String)

/** Часть блюда: продукт и его вес в граммах в одной порции. */
data class Part(val food: Food, val grams: Int)

/**
 * База отдельных продуктов (`foods.json`, собирается `tools/foods/gen.py` из USDA FoodData Central)
 * и состав встроенных блюд (`compositions.json`): из состава считаются проценты, вес порции и пищевая ценность.
 */
object Foods {
    val CATS = listOf(
        "meat" to "Мясо и птица", "fish" to "Рыба", "dairy" to "Молочное и яйца", "grains" to "Крупы и гарниры",
        "veg" to "Овощи", "fruit" to "Фрукты", "nuts" to "Орехи", "fats" to "Масла и соусы", "sweet" to "Сладкое",
    )

    private fun res(name: String) = Foods::class.java.getResourceAsStream("/ru/diary/nutrition/$name")?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: error("$name не найден")

    val all: List<Food> by lazy {
        Json.parseToJsonElement(res("foods.json")).jsonArray.map { e ->
            val o = e.jsonObject
            fun d(k: String) = o[k]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            Food(
                o.getValue("id").jsonPrimitive.content, o.getValue("name").jsonPrimitive.content, o.getValue("cat").jsonPrimitive.content,
                Nutr(d("kcal"), d("p"), d("f"), d("c"), d("fiber"), d("sugar")), o["src"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }

    val byId: Map<String, Food> by lazy { all.associateBy { it.id } }

    /** Продукты, которые показываются в списке «Взвесить» (вода нужна только для состава супа). */
    val visible: List<Food> by lazy { all.filter { it.cat != "other" } }

    private val compositions: Map<String, List<Part>> by lazy {
        Json.parseToJsonElement(res("compositions.json")).jsonObject.filterKeys { !it.startsWith("_") }.mapValues { (_, v) ->
            (v as JsonArray).mapNotNull { pair ->
                val a = pair.jsonArray
                byId[a[0].jsonPrimitive.content]?.let { Part(it, a[1].jsonPrimitive.content.toInt()) }
            }
        }
    }

    fun parts(dishId: String): List<Part> = compositions[dishId].orEmpty()

    fun total(parts: List<Part>): Nutr = parts.fold(Nutr.ZERO) { acc, x -> acc + x.food.per100 * (x.grams / 100.0) }

    fun grams(parts: List<Part>): Int = parts.sumOf { it.grams }

    /** Пищевая ценность смеси на 100 г — для взвешивания однородных блюд (плов, салат, суп). */
    fun per100(parts: List<Part>): Nutr = grams(parts).takeIf { it > 0 }?.let { total(parts) * (100.0 / it) } ?: Nutr.ZERO

    /** Доли продуктов в процентах; округление так, чтобы в сумме было ровно 100. */
    fun shares(parts: List<Part>): List<Pair<Food, Int>> {
        val g = grams(parts).takeIf { it > 0 } ?: return emptyList()
        val raw = parts.map { it.food to it.grams * 100.0 / g }
        val floor = raw.map { (f, v) -> f to v.toInt() }.toMutableList()
        var rest = 100 - floor.sumOf { it.second }
        raw.withIndex().sortedByDescending { it.value.second - it.value.second.toInt() }.forEach { (i, _) ->
            if (rest > 0) { floor[i] = floor[i].first to floor[i].second + 1; rest-- }
        }
        return floor.sortedByDescending { it.second }
    }
}

/**
 * Что можно взвесить: отдельный продукт, однородное блюдо из каталога или свой продукт по штрих-коду.
 * `key`: «f:<продукт>», «d:<блюдо>» или «bc:<штрих-код>».
 */
data class WeighItem(val key: String, val name: String, val per100: Nutr, val parts: List<Part> = emptyList(), val product: Product? = null)

object Weigh {
    fun food(f: Food) = WeighItem("f:" + f.id, f.name, f.per100)
    fun dish(d: Dish): WeighItem? = Foods.parts(d.id).takeIf { it.isNotEmpty() }?.let { WeighItem("d:" + d.id, d.name, Foods.per100(it), it) }
    fun product(p: Product) = WeighItem(PRODUCT_PREFIX + p.code, Products.title(p), Nutr(p.kcal, p.p, p.f, p.c, p.fiber))

    /** Запись взвешенного: пищевая ценность пересчитывается на граммы. */
    fun eat(item: WeighItem, grams: Int, date: String, time: String, now: Long): Entry {
        item.product?.let { return Products.eat(it, grams, date, time, now) }
        val n = item.per100 * (grams / 100.0)
        return Entry(
            Ids.uid(now), date, time, WEIGHED_PREFIX + item.key, item.name, grams.toDouble(),
            Js.round(n.kcal).toInt(), Js.round(n.p).toInt(), true, fat = Js.round(n.f).toInt(), carbs = Js.round(n.c).toInt(), fiber = round1(n.fiber),
        )
    }

    fun round1(v: Double) = (v * 10).roundToInt() / 10.0
}

const val WEIGHED_PREFIX = "w:"
