package ru.diary.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import ru.diary.core.ui.pressClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.ui.BackLink
import ru.diary.core.ui.ChoiceChipsRow
import ru.diary.core.ui.DiarySheet
import ru.diary.core.ui.IconBubble
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.SegmentedControl
import ru.diary.core.ui.SheetRow
import ru.diary.core.util.Js
import ru.diary.nutrition.domain.Catalog
import ru.diary.nutrition.domain.Foods
import ru.diary.nutrition.domain.NutritionData
import ru.diary.nutrition.domain.Weigh
import ru.diary.nutrition.domain.WeighItem

private const val DISHES = "dishes"
private const val MINE = "mine"

/** Все варианты для взвешивания: отдельные продукты, однородные блюда каталога и свои продукты по штрих-коду. */
fun weighItems(data: NutritionData): List<Pair<String, WeighItem>> =
    Foods.visible.map { it.cat to Weigh.food(it) } +
        Catalog.builtin.mapNotNull { d -> Weigh.dish(d)?.let { DISHES to it } } +
        data.products.map { p -> MINE to Weigh.product(p).copy(product = p) }

/** Значок категории продукта для списка. */
private fun catIcon(cat: String) = when (cat) {
    "meat" -> "flame"; "fish" -> "dish"; "dairy" -> "sun"; "grains" -> "star"; "veg" -> "target"; "fruit" -> "sun"
    "nuts" -> "star"; DISHES -> "dish"; MINE -> "barcode"; else -> "dish"
}

/**
 * «Взвесить»: выбираете продукт (куриная грудка, помидор) или однородное блюдо (плов, салат), указываете граммы —
 * калории, белки, жиры, углеводы и клетчатка считаются сами. Всё в одной всплывающей панели в два шага.
 */
@Composable
fun WeighSheet(vm: NutritionViewModel, data: NutritionData, date: String, today: String, initial: WeighItem? = null, onDismiss: () -> Unit) {
    val items = remember(data.products) { weighItems(data) }
    var key by rememberSaveable { mutableStateOf(initial?.key) }
    val chosen = initial?.takeIf { it.key == key } ?: items.firstOrNull { it.second.key == key }?.second
    DiarySheet(
        onDismiss, title = if (chosen == null) "Взвесить" else null, subtitle = "Продукт или однородное блюдо — по граммам", icon = "scale",
    ) { close ->
        if (chosen == null) WeighList(items) { key = it.key }
        else WeighAmount(chosen, date, today, onBack = { key = null }) { grams -> vm.eatWeighed(chosen, grams, date); close() }
    }
}

@Composable
private fun WeighList(items: List<Pair<String, WeighItem>>, onPick: (WeighItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("all") }
    val cats = buildList {
        add("all" to "Все"); addAll(Foods.CATS); add(DISHES to "Блюда")
        if (items.any { it.first == MINE }) add(MINE to "Мои продукты")
    }
    LabeledField("Поиск", query, { query = it }, placeholder = "Например, грудка или помидор", maxLength = 40)
    ChoiceChipsRow(cats, cat, { cat = it })
    val q = query.trim().lowercase()
    val list = items.filter { (c, it) -> (cat == "all" || c == cat) && (q.isEmpty() || it.name.lowercase().contains(q)) }
    if (list.isEmpty()) Muted("Ничего не нашлось. Отсканируйте упаковку или выберите похожий продукт.")
    list.take(80).forEach { (c, it) ->
        val n = it.per100
        SheetRow(
            it.name, { onPick(it) }, subtitle = "${Js.fmt(Js.round(n.kcal))} ккал · Б ${fmt1(n.p)} · Ж ${fmt1(n.f)} · У ${fmt1(n.c)} на 100 г",
            leading = { IconBubble(catIcon(c), if (c == DISHES) DiaryTheme.extras.accent else MaterialTheme.colorScheme.primary) },
        )
    }
    if (list.size > 80) Muted("Показаны первые 80. Уточните поиск.")
}

@Composable
private fun WeighAmount(item: WeighItem, date: String, today: String, onBack: () -> Unit, onSave: (Int) -> Unit) {
    var choice by rememberSaveable(item.key) { mutableStateOf("100") }
    var own by rememberSaveable(item.key) { mutableStateOf("") }
    val grams = if (choice == "own") own.toIntOrNull() ?: 0 else choice.toInt()
    BackLink("Все продукты", onBack)
    Column {
        Text(item.name, style = MaterialTheme.typography.titleLarge)
        Muted("На 100 г")
    }
    MacroRow(item.per100.kcal, item.per100.p, item.per100.f, item.per100.c)
    Muted("Клетчатка ${fmt1(item.per100.fiber)} г на 100 г" + if (item.per100.sugar > 0) " · сахара ${fmt1(item.per100.sugar)} г" else "")
    if (item.parts.isNotEmpty()) Composition(Foods.shares(item.parts).map { Triple(it.first.id, it.first.name, it.second) })
    Text("Сколько съели", style = MaterialTheme.typography.titleSmall)
    SegmentedControl(listOf("50" to "50 г", "100" to "100 г", "150" to "150 г", "200" to "200 г", "own" to "Своё"), choice, { choice = it })
    if (choice == "own") LabeledField("Вес, г", own, { own = it.filter(Char::isDigit) }, numeric = true, maxLength = 4)
    if (grams > 0) {
        val n = item.per100 * (grams / 100.0)
        Text("$grams г", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        MacroRow(Js.round(n.kcal), Weigh.round1(n.p), Weigh.round1(n.f), Weigh.round1(n.c))
        Muted("Клетчатка ${fmt1(n.fiber)} г")
    }
    PrimaryButton("Записать · " + Dates.label(date, today).lowercase(), { onSave(grams) }, Modifier.fillMaxWidth(), enabled = grams in 1..5000)
}

/** Состав блюда в процентах: «60% минтай · 30% картофель…». Если задан `onOpen`, строка ведёт к продукту. */
@Composable
fun Composition(shares: List<Triple<String, String, Int>>, onOpen: ((String) -> Unit)? = null) {
    Text("Состав порции", style = MaterialTheme.typography.titleSmall)
    shares.forEach { (id, name, pct) ->
        val click = onOpen?.let { f -> Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).pressClickable(scale = 0.98f) { f(id) } } ?: Modifier
        Row(
            Modifier.fillMaxWidth().then(click).padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$pct%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                ru.diary.core.charts.MeterBar(pct / 100f, "$name: $pct%", height = 5.dp)
            }
            if (onOpen != null) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

fun fmt1(v: Double): String = Js.fmt(Weigh.round1(v))
