package ru.diary.nutrition.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import ru.diary.nutrition.domain.amountLabel
import ru.diary.core.ui.ToggleRow
import ru.diary.core.ui.TileOption
import ru.diary.core.ui.IconTiles
import ru.diary.core.ui.SegmentedControl
import androidx.compose.foundation.background
import ru.diary.core.charts.BarPart
import ru.diary.core.charts.BarColumn
import ru.diary.core.charts.StackedBarChart
import ru.diary.core.charts.RingIndicator
import ru.diary.core.ui.EmptyState
import ru.diary.core.ui.IconBubble
import ru.diary.core.ui.HeroStat
import ru.diary.core.ui.HeroHeader
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.diary.core.charts.GroupColumn
import ru.diary.core.charts.GroupedBarChart
import ru.diary.core.charts.Legend
import ru.diary.core.charts.LegendItem
import ru.diary.core.charts.MeterBar
import ru.diary.core.contract.DiaryContract
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.theme.ThemeMode
import ru.diary.core.ui.Appear
import ru.diary.core.ui.Callout
import ru.diary.core.ui.ChoiceChipsRow
import ru.diary.core.update.UpdateSection
import ru.diary.nutrition.container
import ru.diary.core.ui.ChoiceChips
import ru.diary.core.ui.ConfirmBox
import ru.diary.core.ui.DangerButton
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.MinTouch
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.Reveal
import ru.diary.core.ui.ScreenTitle
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.ui.SectionTitle
import ru.diary.core.util.Js
import ru.diary.nutrition.domain.CAT_NAME
import ru.diary.nutrition.domain.CATS
import ru.diary.nutrition.domain.Dish
import ru.diary.nutrition.domain.Entry
import ru.diary.nutrition.domain.Goals
import ru.diary.nutrition.domain.NutritionData
import ru.diary.nutrition.domain.NutritionLogic
import ru.diary.nutrition.domain.NutritionTransfer
import ru.diary.nutrition.domain.PORTIONS
import ru.diary.nutrition.domain.portionLabel

@Composable
fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

// ---------- Блюда ----------

/** Вкладка «Мои продукты» в ряду категорий. */
private const val PRODUCTS_TAB = "products"

/** Авторы фотографий блюд: свободные лицензии требуют их указать. */
@Composable
private fun PhotoCredits() {
    val credits = DishPhotos.credits(LocalContext.current)
    var open by rememberSaveable { mutableStateOf(false) }
    Muted("Фотографии взяты из открытых источников (Flickr, Wikimedia Commons) под свободными лицензиями Creative Commons. Снимки уменьшены и обрезаны.")
    QuietButton(if (open) "Скрыть авторов" else "Показать авторов", { open = !open })
    Reveal(open) {
        val names = ru.diary.nutrition.domain.Catalog.builtin.associate { it.id to it.name }
        credits.forEach { (id, c) ->
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(names[id] ?: id, style = MaterialTheme.typography.titleSmall)
                Muted("«${c.title}» — ${c.author}, ${c.license}")
                if (c.source.isNotEmpty()) Muted(c.source)
            }
        }
    }
}

/** Значок и цвет категории блюда. */
private fun catIcon(cat: String) = when (cat) { "breakfast" -> "sun"; "side" -> "star"; "snack" -> "flame"; else -> "dish" }

@Composable
private fun catColor(cat: String) = when (cat) {
    "breakfast" -> DiaryTheme.extras.protein
    "side" -> DiaryTheme.extras.cardio
    "snack" -> DiaryTheme.extras.volley
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun DishesScreen(vm: NutritionViewModel, data: NutritionData, today: String, date: String, onDate: (String) -> Unit) {
    var portion by rememberSaveable { mutableDoubleStateOf(1.0) }
    var cat by rememberSaveable { mutableStateOf("all") }
    var byAge by rememberSaveable { mutableStateOf(true) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var askDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val y = Dates.addDays(today, -1)

    TabColumn {
        HeroHeader("Блюда", subtitle = "Записываю на: " + Dates.label(date, today).lowercase(), icon = "dish") {
            val t = NutritionLogic.totals(data, date)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("${t.protein} г", "белка", Modifier.weight(1f))
                HeroStat("${t.kcal}", "ккал", Modifier.weight(1f))
            }
        }
        ScanButtons(vm)
        val scan by vm.scan.collectAsStateWithLifecycle()
        ScanPanel(vm, scan, date, today)
        Muted("Записываю на")
        SegmentedControl(
            buildList { add(today to "Сегодня"); add(y to "Вчера"); if (date != today && date != y) add(date to Dates.label(date, today)) },
            date, onDate,
        )
        Muted("Порция")
        SegmentedControl(PORTIONS.map { it.first to it.second }, portion, { portion = it })
        ChoiceChipsRow(CATS + (PRODUCTS_TAB to "Мои продукты"), cat, { cat = it })
        if (cat == PRODUCTS_TAB) {
            data.products.forEachIndexed { i, p -> Appear(index = i) { ProductRow(vm, p) } }
            if (data.products.isEmpty()) EmptyState("barcode", "Здесь будут продукты, которые вы отсканировали. Нажмите «Сканировать» и наведите камеру на штрих-код упаковки.")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Muted("Порядок: " + if (byAge) "давно не ел сверху" else "по алфавиту")
                QuietButton(if (byAge) "По алфавиту" else "Давно не ел сверху", { byAge = !byAge })
            }
            val list = NutritionLogic.ordered(data, today, byAge).filter { cat == "all" || it.cat == cat }
            list.forEachIndexed { i, d -> Appear(index = i) { DishRow(vm, data, d, today, date, portion, open == d.id, { open = if (open == d.id) null else d.id }, askDelete == d.id, { askDelete = it }) } }
            if (list.isEmpty()) EmptyState("dish", "В этой категории пока нет блюд.")
        }

        QuietButton(if (addOpen) "Скрыть форму" else "Добавить своё блюдо", { addOpen = !addOpen })
        Reveal(addOpen) { AddDishForm(vm) { addOpen = false } }
    }
}

@Composable
private fun DishRow(
    vm: NutritionViewModel, data: NutritionData, d: Dish, today: String, date: String, portion: Double, open: Boolean, onToggle: () -> Unit,
    askDelete: Boolean, setAsk: (String?) -> Unit,
) {
    val last = NutritionLogic.lastEaten(data, d.id)
    val ago = last?.let { Dates.daysBetween(it, today) }
    val txt = when {
        ago == null -> "ещё не ел"; ago == 0 -> "ел сегодня"; ago == 1 -> "ел вчера"
        else -> "ел $ago ${Js.plural(ago, "день", "дня", "дней")} назад"
    }
    val stale = ago == null || ago >= 7
    DiaryCard(onClick = onToggle) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!d.custom && d.id in DishPhotos.credits(LocalContext.current)) {
                DishPhoto(d.id, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)), sample = 4)
            } else IconBubble(catIcon(d.cat), catColor(d.cat), size = 44.dp)
            Column(
                Modifier.weight(1f).heightIn(min = MinTouch).semantics { stateDescription = if (open) "развёрнуто" else "свёрнуто" },
            ) {
                Text(d.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "$txt · ${d.kcal} ккал · Б ${d.p} г", style = MaterialTheme.typography.bodySmall,
                    color = if (stale) DiaryTheme.extras.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimaryButton("Съел", { vm.eat(d, date, portion) }, Modifier.semantics { contentDescription = "Съел: ${d.name}" })
        }
        Reveal(open) { DishDetail(vm, d, askDelete, setAsk) }
    }
}

@Composable
private fun DishDetail(vm: NutritionViewModel, d: Dish, askDelete: Boolean, setAsk: (String?) -> Unit) {
    val credit = if (d.custom) null else DishPhotos.credits(LocalContext.current)[d.id]
    if (credit != null) {
        DishPhoto(d.id, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)))
        Muted("Фото: ${credit.author} · ${credit.license}")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("${d.kcal} ккал", style = MaterialTheme.typography.titleSmall)
        Text("Б ${d.p} г", style = MaterialTheme.typography.titleSmall, color = DiaryTheme.extras.protein)
        if (d.f != null) Text("Ж ${d.f} г", style = MaterialTheme.typography.titleSmall)
        if (d.c != null) Text("У ${d.c} г", style = MaterialTheme.typography.titleSmall)
    }
    if (d.custom) {
        if (d.notes.isNotEmpty()) { Text("Рецепт и заметки", style = MaterialTheme.typography.titleSmall); Text(d.notes, style = MaterialTheme.typography.bodyMedium) }
        else Muted("Рецепт не добавлен.")
        if (askDelete) ConfirmBox("Удалить блюдо «${d.name}»? Записи в дневнике останутся.", "Да, удалить", { vm.deleteDish(d.id); setAsk(null) }, { setAsk(null) }, cancelLabel = "Нет")
        else DangerButton("Удалить блюдо", { setAsk(d.id) })
    } else {
        Muted("На одну порцию, значения приблизительные. Время: ${d.time}.")
        d.batch?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (d.ing.isNotEmpty()) {
            Text("Ингредиенты", style = MaterialTheme.typography.titleSmall)
            d.ing.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (d.steps.isNotEmpty()) {
            Text("Как готовить", style = MaterialTheme.typography.titleSmall)
            d.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun AddDishForm(vm: NutritionViewModel, onSaved: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("main") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    Callout {
        LabeledField("Название", name, { name = it }, maxLength = 120)
        Muted("Тип")
        IconTiles(CAT_NAME.map { (k, name) -> TileOption(k, catIcon(k), name, catColor(k)) }, cat, { cat = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField("Ккал на порцию", kcal, { kcal = it }, Modifier.weight(1f), numeric = true)
            LabeledField("Белок, г", protein, { protein = it }, Modifier.weight(1f), numeric = true)
        }
        LabeledField("Рецепт и заметки", notes, { notes = it }, singleLine = false, minLines = 3, maxLength = 4000)
        PrimaryButton("Сохранить блюдо", {
            if (vm.addDish(name, cat, kcal, protein, notes)) { name = ""; kcal = ""; protein = ""; notes = ""; onSaved() }
        })
    }
}

// ---------- Дневник ----------

@Composable
fun DiaryScreen(vm: NutritionViewModel, data: NutritionData, today: String, date: String, onDate: (String) -> Unit, link: TrainingLink) {
    val es = NutritionLogic.entriesOn(data, date)
    val t = NutritionLogic.totals(data, date)
    var oopOpen by rememberSaveable { mutableStateOf(false) }
    var oopName by rememberSaveable { mutableStateOf("") }
    var oopKcal by rememberSaveable { mutableStateOf("") }
    var oopProtein by rememberSaveable { mutableStateOf("") }

    TabColumn {
        HeroHeader(Dates.label(date, today), subtitle = "Дневник питания", icon = "book") {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("${es.size}", Js.plural(es.size, "приём", "приёма", "приёмов"), Modifier.weight(1f))
                HeroStat("${t.kcal}", "ккал", Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("‹ Раньше", { onDate(Dates.addDays(date, -1)) }, Modifier.semantics { contentDescription = "Предыдущий день" })
            SecondaryButton("Позже ›", { onDate(Dates.addDays(date, 1)) }, enabled = date < today, modifier = Modifier.semantics { contentDescription = "Следующий день" })
        }
        DiaryCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RingIndicator(
                    t.protein.toFloat() / Goals.proteinMin, "${t.protein}", "из ${Goals.proteinMin} г",
                    color = if (t.protein >= Goals.proteinMin) MaterialTheme.colorScheme.primary else DiaryTheme.extras.protein, size = 96.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Белок за день", style = MaterialTheme.typography.titleMedium)
                    Muted("Цель ${Goals.proteinMin}–${Goals.proteinMax} г")
                    MeterBar(
                        (t.kcal.toFloat() / Goals.kcal).coerceAtMost(1f), "Калории за день: ${t.kcal} из примерно ${Goals.kcal}",
                        color = DiaryTheme.extras.accent,
                    )
                    Muted("${t.kcal} из ~${Goals.kcal} ккал")
                }
            }
            if (t.fat > 0 || t.carbs > 0) MacroRow(t.kcal.toDouble(), t.protein.toDouble(), t.fat.toDouble(), t.carbs.toDouble())
            Muted("Значения приблизительные. Жиры и углеводы — по блюдам каталога и отсканированным продуктам.")
        }
        if (link is TrainingLink.Ready) DayBalance(link, date, t.kcal, t.protein)

        if (es.isEmpty()) EmptyState("book", "За этот день записей нет. Отметьте блюдо кнопкой «Съел» на вкладке «Блюда».")
        else DiaryCard { es.forEach { EntryRow(it) { vm.removeEntry(it) } } }

        QuietButton(if (oopOpen) "Скрыть форму" else "Приём вне плана (кафе, гости)", { oopOpen = !oopOpen })
        Reveal(oopOpen) {
            Callout {
                Muted("Калории и белок можно не указывать. Запись просто отметится как приём вне плана.")
                LabeledField("Что съели", oopName, { oopName = it }, maxLength = 120, placeholder = "Например, шаурма в кафе")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField("Ккал, примерно", oopKcal, { oopKcal = it }, Modifier.weight(1f), numeric = true)
                    LabeledField("Белок, г", oopProtein, { oopProtein = it }, Modifier.weight(1f), numeric = true)
                }
                PrimaryButton("Записать", { vm.saveOutOfPlan(oopName, oopKcal, oopProtein, date); oopName = ""; oopKcal = ""; oopProtein = ""; oopOpen = false })
            }
        }
    }
}

@Composable
private fun EntryRow(e: Entry, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(e.time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                if (!e.plan) ru.diary.core.ui.Muted("вне плана")
            }
            Muted(
                (if (e.plan) amountLabel(e) + " · " else "") + (if (e.kcal > 0) "${e.kcal} ккал" else "ккал не указаны") + (if (e.protein > 0) " · Б ${e.protein} г" else "") +
                    (e.fat?.let { " · Ж $it г" } ?: "") + (e.carbs?.let { " · У $it г" } ?: ""),
            )
        }
        QuietButton("Удалить", onDelete, Modifier.semantics { contentDescription = "Удалить запись: ${e.name}" })
    }
}

/**
 * «Баланс дня»: съедено и потрачено на занятиях. Расход — оценка пользователя из «Тренировок», а не общий расход,
 * и баланс не является медицинским выводом.
 */
@Composable
private fun DayBalance(link: TrainingLink.Ready, date: String, eaten: Int, protein: Int) {
    val day = link.days[date]
    DiaryCard {
        Text("Баланс дня", style = MaterialTheme.typography.titleMedium)
        if (day == null) { Muted("За этот день нет данных из «Тренировок»."); return@DiaryCard }
        val burned = day.kcalBurned
        val diff = eaten - burned
        val mx = maxOf(eaten, burned, 1).toFloat()
        Text("Съедено: $eaten ккал", style = MaterialTheme.typography.bodyMedium)
        MeterBar(eaten / mx, "Съедено $eaten ккал", color = MaterialTheme.colorScheme.primary)
        Text("Потрачено на тренировках: $burned ккал", style = MaterialTheme.typography.bodyMedium)
        MeterBar(burned / mx, "Потрачено на тренировках $burned ккал", color = DiaryTheme.extras.volley)
        Text(
            "Разница: " + (if (diff > 0) "+" else "") + "$diff ккал", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { contentDescription = "Разница между съеденным и потраченным на тренировках: $diff ккал" },
        )
        Muted("«Потрачено» — ваша оценка расхода на занятиях, без базового обмена. Это не общий расход и не медицинский вывод.")
        val kg = link.meta.lastWeightKg
        if (link.meta.proteinEnabled && kg != null) {
            val lo = Js.round(kg * 1.6).toInt(); val hi = Js.round(kg * 2.0).toInt()
            Text("Белок по весу ${Js.fmt(kg)} кг: $protein из $lo–$hi г", style = MaterialTheme.typography.bodyMedium)
            MeterBar((protein.toFloat() / lo).coerceAtMost(1f), "Белок $protein из нормы $lo–$hi граммов", color = DiaryTheme.extras.protein)
        }
        dayHint(day.dayType)?.let { Callout { Text(it, style = MaterialTheme.typography.bodyMedium) } }
    }
}

/** Подсказка по типу дня: в день зала или волейбола — про белок и углеводы, в день отдыха — ничего. */
fun dayHint(type: String): String? = when (type) {
    DiaryContract.DAY_GYM -> "День зала: после тренировки добавьте белок, а до неё — углеводы (каша, макароны, банан)."
    DiaryContract.DAY_VOLLEY -> "День волейбола: до игры нужны углеводы, после игры — белок (творог, яйца, курица)."
    else -> null
}

// ---------- Неделя ----------

@Composable
fun WeekScreen(data: NutritionData, today: String, link: TrainingLink) {
    val w = NutritionLogic.week(data, today)
    TabColumn {
        HeroHeader("Неделя", subtitle = "Белок за 7 дней", icon = "chart") {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat(if (w.loggedDays > 0) "${w.avgProtein} г" else "–", "белка в среднем", Modifier.weight(1f))
                HeroStat("${w.loggedDays} из 7", "дней с записями", Modifier.weight(1f))
            }
        }
        DiaryCard {
            val primary = MaterialTheme.colorScheme.primary
            val low = DiaryTheme.extras.protein
            StackedBarChart(
                w.days.map { (d, p) -> BarColumn(Dates.ddmm(d), listOf(BarPart(p.toDouble(), if (p >= Goals.proteinMin) primary else low))) },
                "Белок по дням за неделю: " + w.days.joinToString("; ") { (d, p) -> "${Dates.ddmm(d)} — $p г" },
                goal = Goals.proteinMin.toDouble(), minMax = Goals.proteinMin.toDouble(),
            )
            Legend(listOf(LegendItem("Норма белка", primary), LegendItem("Меньше ${Goals.proteinMin} г", low)))
        }
        w.days.forEach { (d, p) ->
            val dt = Dates.parse(d)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Dates.label(d, today).take(14), Modifier.weight(0.35f), style = MaterialTheme.typography.bodySmall)
                MeterBar(
                    (p.toFloat() / (Goals.proteinMax + 20)).coerceAtMost(1f), "${dt.dayOfMonth}.${dt.monthValue}: $p г белка", Modifier.weight(0.5f),
                    color = if (p >= Goals.proteinMin) MaterialTheme.colorScheme.primary else DiaryTheme.extras.protein,
                    mark = Goals.proteinMin.toFloat() / (Goals.proteinMax + 20),
                )
                Text(if (p > 0) "$p г" else "–", Modifier.weight(0.15f), style = MaterialTheme.typography.labelLarge)
            }
        }
        Muted("Вертикальная черта на шкале: ${Goals.proteinMin} г белка.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(if (w.loggedDays > 0) "${w.avgProtein} г" else "–", "белка в день в среднем", Modifier.weight(1f))
            Stat("${w.total}", Js.plural(w.total, "приём записан", "приёма записано", "приёмов записано"), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(if (w.total > 0) "${w.outOfPlanShare}%" else "–", "вне плана (норма до 20%)", Modifier.weight(1f))
            Stat("${w.fish}", "блюд с рыбой (цель 2 в неделю)", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${w.variety}", "разных блюд за неделю", Modifier.weight(1f))
            Stat("${w.loggedDays} из 7", "дней с записями", Modifier.weight(1f))
        }
        Callout { w.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }

        if (link is TrainingLink.Ready) {
            SectionTitle("Съедено и потрачено")
            val cols = w.days.map { (d, _) ->
                GroupColumn(Dates.ddmm(d), NutritionLogic.totals(data, d).kcal.toDouble(), (link.days[d]?.kcalBurned ?: 0).toDouble())
            }
            GroupedBarChart(
                cols, MaterialTheme.colorScheme.primary, DiaryTheme.extras.volley,
                "Ккал по дням за неделю: съедено и потрачено на тренировках. " + cols.joinToString("; ") { "${it.label}: съедено ${it.a.toInt()}, потрачено ${it.b.toInt()}" },
            )
            Legend(listOf(LegendItem("Съедено", MaterialTheme.colorScheme.primary), LegendItem("Потрачено на тренировках", DiaryTheme.extras.volley)))
            Muted("Расход на тренировках — оценка из приложения «Тренировки», без базового обмена.")
        }

        SectionTitle("Пора вспомнить")
        DiaryCard {
            NutritionLogic.stale(data, today).forEach { (d, age) ->
                Column(Modifier.padding(vertical = 2.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                    Muted(if (age == null) "ещё не ели" else "$age ${Js.plural(age, "день", "дня", "дней")} назад")
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$value $label" },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = DiaryTheme.extras.glass,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
    ) {
        Column(
            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), androidx.compose.ui.graphics.Color.Transparent)))
                .padding(14.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, DiaryTheme.extras.accent))))
            Muted(label)
        }
    }
}

// ---------- Настройки ----------

private const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

@Composable
fun SettingsScreen(vm: NutritionViewModel, link: TrainingLink) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf<NutritionTransfer.Draft?>(null) }
    var resetAsk by rememberSaveable { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            val text = vm.exportText()
            val ok = text != null && runCatching {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("нет потока")
            }.isSuccess
            vm.message(if (ok) "Файл сохранён" else "Не получилось сохранить файл")
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0L
                    while (true) { val n = input.read(buf); if (n < 0) break; total += n; if (total > MAX_IMPORT_BYTES) error("big"); out.write(buf, 0, n) }
                    out.toString(Charsets.UTF_8.name())
                }
            }.getOrNull()
            if (text == null) vm.message("Не удалось прочитать файл")
            else {
                val d = vm.parseImport(text)
                if (d == null) vm.message("Не удалось прочитать JSON. Нужен файл, экспортированный из этого дневника.")
                draft = d
            }
        }
    }

    TabColumn {
        HeroHeader("Настройки", subtitle = "Тема, данные и обновления", icon = "sliders")
        SectionTitle("Тема")
        SegmentedControl(listOf(ThemeMode.Light to "Светлая", ThemeMode.Dark to "Тёмная", ThemeMode.System to "Системная"), s.theme, { vm.setTheme(it) })
        Muted("«Системная» следует настройке телефона.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ToggleRow("Цвета обоев (Material You) вместо фирменных", s.dynamicColor, { vm.setDynamicColor(it) })
        }
        SectionTitle("Где хранятся данные")
        Muted("Данные хранятся только на этом телефоне и никуда не отправляются. Делайте экспорт, чтобы не потерять историю.")
        SectionTitle("Сканирование продуктов")
        ToggleRow(
            "Искать продукты в Open Food Facts", s.onlineLookup, { vm.setOnlineLookup(it) },
            subtitle = "Открытая база продуктов. Туда уходит только номер штрих-кода. Без поиска продукт вводится с этикетки один раз и дальше находится сразу.",
        )
        SectionTitle("Фотографии блюд")
        PhotoCredits()
        SectionTitle("Обновления")
        UpdateSection(ctx.container.updater)
        if (DiaryContract.SHARING_ENABLED) {
            SectionTitle("Связь с «Тренировками»")
            Muted(
                if (link is TrainingLink.Ready) "Приложение «Тренировки» найдено: в дневнике показывается баланс дня и график «съедено и потрачено»."
                else "Если установить приложение «Тренировки», здесь появятся баланс дня и подсказки по дням занятий. Без него всё работает как обычно.",
            )
        }
        SectionTitle("Экспорт")
        Muted("Файл JSON с записями, своими блюдами и каталогом рецептов. Его можно перенести в веб-версию или обратно сюда.")
        PrimaryButton("Сохранить JSON в файл", { export.launch("dnevnik-raciona-${Dates.today()}.json") })
        SectionTitle("Импорт")
        Muted("Выберите файл JSON, экспортированный из этого дневника или из веб-версии.")
        SecondaryButton("Выбрать файл", { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        draft?.let { d ->
            Callout {
                Text(
                    "Найдено записей: ${d.log.size}, своих блюд: ${d.custom.size}" + (if (d.skipped > 0) ". Пропущено повреждённых: ${d.skipped}" else "") + ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Объединить", { vm.applyImport(d, NutritionTransfer.Mode.Merge); draft = null })
                    SecondaryButton("Заменить", { vm.applyImport(d, NutritionTransfer.Mode.Replace); draft = null })
                    QuietButton("Отмена", { draft = null })
                }
            }
        }
        SectionTitle("Удалить всё")
        if (!resetAsk) DangerButton("Удалить все данные", { resetAsk = true })
        else ConfirmBox("Все записи и свои блюда будут удалены. Сначала сделайте экспорт, если хотите сохранить историю.", "Да, удалить", { vm.resetAll(); resetAsk = false }, { resetAsk = false })
    }
}
