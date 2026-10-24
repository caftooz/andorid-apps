package ru.diary.nutrition.ui

import ru.diary.core.ui.DiarySheet
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.ui.DangerButton
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.IconBadge
import ru.diary.core.ui.IconBubble
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.Reveal
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.ui.SegmentedControl
import ru.diary.core.util.Js
import ru.diary.nutrition.domain.Product
import ru.diary.nutrition.domain.Products

/** Кнопки «Сканировать» и «Ввести код» над списком блюд. Камера открывается экраном библиотеки ZXing. */
@Composable
fun ScanButtons(vm: NutritionViewModel) {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(vm::onBarcode) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Сканировать", {
            launcher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.EAN_13, ScanOptions.EAN_8, ScanOptions.UPC_A, ScanOptions.UPC_E, ScanOptions.ITF)
                    .setPrompt("Наведите камеру на штрих-код продукта")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN),
            )
        }, Modifier.weight(1f))
        SecondaryButton("Ввести код", { typing = true })
    }
    if (typing) DiarySheet({ typing = false }, title = "Ввести штрих-код", subtitle = "8–14 цифр под полосками", icon = "barcode") { close ->
        LabeledField("Цифры под штрих-кодом", code, { code = it.filter(Char::isDigit) }, numeric = true, maxLength = 14)
        PrimaryButton("Найти", { val c = code; code = ""; close(); vm.onBarcode(c) }, Modifier.fillMaxWidth(), enabled = code.length >= 8)
    }
}

/**
 * Всплывающая панель сканирования: поиск, выбор граммов или ручной ввод КБЖУ.
 * Панель одна на все шаги, поэтому при переходе «не найден → ввод → граммы» она не закрывается.
 */
@Composable
fun ScanPanel(vm: NutritionViewModel, scan: ScanState, date: String, today: String) {
    if (scan == ScanState.Idle) return
    DiarySheet(vm::cancelScan) { close -> ScanContent(vm, scan, date, today, close) }
}

@Composable
private fun ScanContent(vm: NutritionViewModel, scan: ScanState, date: String, today: String, close: () -> Unit) {
    when (scan) {
        ScanState.Idle -> Unit
        is ScanState.Looking -> Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Column(Modifier.weight(1f)) {
                    Text("Ищу продукт", style = MaterialTheme.typography.titleMedium)
                    Muted("Штрих-код ${scan.code} · Open Food Facts")
                }
                QuietButton("Отмена", close)
            }
        }
        is ScanState.Ready -> ReadyCard(vm, scan.product, scan.saved, date, today, close)
        is ScanState.Manual -> ManualCard(vm, scan, close)
    }
}

@Composable
private fun ReadyCard(vm: NutritionViewModel, p: Product, saved: Boolean, date: String, today: String, close: () -> Unit) {
    // Граммы: 100 г, вся упаковка (если вес известен) или своё значение.
    var choice by rememberSaveable(p.code) { mutableStateOf(if (p.packG != null) "pack" else "100") }
    var own by rememberSaveable(p.code) { mutableStateOf("") }
    var askDelete by rememberSaveable(p.code) { mutableStateOf(false) }
    val grams = when (choice) {
        "pack" -> p.packG ?: 100
        "own" -> own.toIntOrNull() ?: 0
        else -> 100
    }
    val k = grams / 100.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge("barcode", MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Muted(listOf(p.brand, if (p.source == "off") "из Open Food Facts" else "ваш продукт").filter { it.isNotEmpty() }.joinToString(" · "))
            }
        }
        Muted("На 100 г")
        MacroRow(p.kcal, p.p, p.f, p.c)
        SegmentedControl(
            buildList {
                add("100" to "100 г")
                p.packG?.let { add("pack" to "$it г") }
                add("own" to "Своё")
            },
            choice, { choice = it },
        )
        if (choice == "pack") Muted("Вся упаковка — ${p.packG} г")
        Reveal(choice == "own") {
            LabeledField("Сколько съели, г", own, { own = it.filter(Char::isDigit) }, numeric = true, maxLength = 4)
        }
        if (grams > 0) {
            Text(
                "$grams г: ${Js.round(p.kcal * k).toInt()} ккал · Б ${Js.round(p.p * k).toInt()} · Ж ${Js.round(p.f * k).toInt()} · У ${Js.round(p.c * k).toInt()} г",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            )
        }
        PrimaryButton(
            "Съел · " + ru.diary.core.date.Dates.label(date, today).lowercase(), { vm.eatProduct(p, grams, date, closePanel = false); close() },
            Modifier.fillMaxWidth(), enabled = grams in 1..5000,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Изменить", { vm.editProduct(p) })
            QuietButton("Отмена", close)
        }
        if (saved) {
            if (askDelete) ru.diary.core.ui.ConfirmBox("Удалить «${p.name}» из моих продуктов? Записи в дневнике останутся.", "Да, удалить", { vm.deleteProduct(p.code) }, { askDelete = false }, cancelLabel = "Нет")
            else DangerButton("Удалить из моих продуктов", { askDelete = true })
        }
    }
}

@Composable
private fun ManualCard(vm: NutritionViewModel, s: ScanState.Manual, close: () -> Unit) {
    val d = s.draft
    fun num(v: Double?) = if (v == null || v == 0.0) "" else Js.fmt(v)
    var name by rememberSaveable(s.code) { mutableStateOf(d?.name.orEmpty()) }
    var brand by rememberSaveable(s.code) { mutableStateOf(d?.brand.orEmpty()) }
    var kcal by rememberSaveable(s.code) { mutableStateOf(num(d?.kcal)) }
    var prot by rememberSaveable(s.code) { mutableStateOf(num(d?.p)) }
    var fat by rememberSaveable(s.code) { mutableStateOf(num(d?.f)) }
    var carb by rememberSaveable(s.code) { mutableStateOf(num(d?.c)) }
    var pack by rememberSaveable(s.code) { mutableStateOf(d?.packG?.toString().orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge("barcode", DiaryTheme.extras.accent)
            Column(Modifier.weight(1f)) {
                Text(if (d == null) "Новый продукт" else "Проверьте данные", style = MaterialTheme.typography.titleMedium)
                Muted("Штрих-код ${s.code}")
            }
        }
        Muted(s.note)
        LabeledField("Название", name, { name = it }, maxLength = 120, placeholder = "Например, творог 5%")
        LabeledField("Бренд, по желанию", brand, { brand = it }, maxLength = 80)
        Muted("Значения на 100 г — как на этикетке")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField("Ккал", kcal, { kcal = it }, Modifier.weight(1f), decimal = true, maxLength = 6)
            LabeledField("Белки, г", prot, { prot = it }, Modifier.weight(1f), decimal = true, maxLength = 5)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField("Жиры, г", fat, { fat = it }, Modifier.weight(1f), decimal = true, maxLength = 5)
            LabeledField("Углеводы, г", carb, { carb = it }, Modifier.weight(1f), decimal = true, maxLength = 5)
        }
        LabeledField("Вес упаковки, г, по желанию", pack, { pack = it.filter(Char::isDigit) }, numeric = true, maxLength = 5)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Сохранить", { vm.saveManualProduct(s.code, name, brand, kcal, prot, fat, carb, pack) })
            QuietButton("Отмена", close)
        }
    }
}

/** Четыре колонки: ккал, белки, жиры, углеводы. */
@Composable
fun MacroRow(kcal: Double, p: Double, f: Double, c: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Macro(Js.fmt(kcal), "ккал", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        Macro(Js.fmt(p), "белки", DiaryTheme.extras.protein, Modifier.weight(1f))
        Macro(Js.fmt(f), "жиры", DiaryTheme.extras.volley, Modifier.weight(1f))
        Macro(Js.fmt(c), "углев.", DiaryTheme.extras.cardio, Modifier.weight(1f))
    }
}

@Composable
private fun Macro(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    androidx.compose.material3.Surface(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = color.copy(alpha = 0.12f),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

/** Строка в списке «Мои продукты». */
@Composable
fun ProductRow(vm: NutritionViewModel, p: Product) {
    DiaryCard(onClick = { vm.openProduct(p) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBubble("barcode", MaterialTheme.colorScheme.primary, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(Products.title(p), style = MaterialTheme.typography.titleSmall)
                Muted("${Js.fmt(p.kcal)} ккал · Б ${Js.fmt(p.p)} · Ж ${Js.fmt(p.f)} · У ${Js.fmt(p.c)} на 100 г")
            }
            PrimaryButton("Съел", { vm.openProduct(p) }, Modifier.semantics { contentDescription = "Съел: ${p.name}" })
        }
    }
}
