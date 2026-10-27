package ru.diary.training.ui

import ru.diary.core.ui.Reveal
import ru.diary.core.ui.DiarySheet
import ru.diary.core.ui.SegmentedControl
import ru.diary.core.ui.TileOption
import ru.diary.core.ui.IconTiles
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.diary.core.ui.Callout
import ru.diary.core.ui.ChoiceChips
import ru.diary.core.ui.ConfirmBox
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.util.Js
import ru.diary.training.domain.Muscles
import ru.diary.training.domain.BuiltinData
import ru.diary.training.domain.ExDef
import ru.diary.training.domain.ExerciseCatalog
import ru.diary.training.domain.TrainingData

/** Одна колонка вкладки: прокрутка, отступы, расстояние между блоками. */
@Composable
fun TabColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

fun kindMeta(d: ExDef): String {
    val reps = if (d.hi != d.lo) "${d.lo}–${d.hi}" else "${d.lo}"
    return Texts.KIND_NAME.getValue(d.kind) + " · ${d.sets} × $reps" + (if (d.kind == "time") " с" else "") + (if (d.custom) " · своё" else "")
}

/** Всплывающая панель выбора упражнения. */
@Composable
fun ExercisePickerSheet(vm: TrainingViewModel, data: TrainingData, excludeIds: List<String>, onDismiss: () -> Unit, onPick: (ExDef) -> Unit) {
    DiarySheet(onDismiss, title = "Добавить упражнение", subtitle = "Каталог и ваши упражнения", icon = "plus") {
        ExercisePicker(vm, data, excludeIds, onPick)
    }
}

/**
 * Выбор упражнения для программы или текущей тренировки: каталог без уже добавленных,
 * удаление своих упражнений и форма создания нового.
 */
@Composable
fun ExercisePicker(
    vm: TrainingViewModel,
    data: TrainingData,
    excludeIds: List<String>,
    onPick: (ExDef) -> Unit,
) {
    val catalog = ExerciseCatalog(data.customEx)
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    // Поиск по названию и по мышцам: «бицепс», «грудь», «жим».
    val list = catalog.all.filter { it.id !in excludeIds }.filter { d ->
        q.isEmpty() || d.name.lowercase().contains(q) ||
            Muscles.forEx(d.id)?.let { l -> (l.primary + l.secondary).any { Muscles.name(it).lowercase().contains(q) } } == true
    }
    var askDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("w") }
    var step by rememberSaveable { mutableDoubleStateOf(1.0) }
    var tip by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabeledField("Поиск", query, { query = it }, placeholder = "Например, жим, бицепс или спина", maxLength = 40)
        if (list.isEmpty()) Muted(if (q.isNotEmpty()) "Ничего не нашлось. Создайте своё упражнение ниже." else "Все упражнения из списка уже добавлены. Создайте своё ниже.")
        list.forEach { d ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Muscles.forEx(d.id)?.let { BodyThumb(it) }
                Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                    Muted(kindMeta(d))
                    Muscles.forEx(d.id)?.let { l -> Muted(l.primary.joinToString(", ") { Muscles.name(it).lowercase() }) }
                }
                if (d.custom) QuietButton("Удалить", { askDelete = d.id }, Modifier.semantics { contentDescription = "Удалить упражнение ${d.name}" })
                SecondaryButton("Добавить", { onPick(d) }, Modifier.semantics { contentDescription = "Добавить: ${d.name}" })
            }
        }
        askDelete?.let { id ->
            ConfirmBox(
                "Удалить упражнение «${catalog.nameOf(id)}»? Оно пропадёт из всех программ. История тренировок останется.",
                "Да, удалить", { vm.deleteCustomEx(id); askDelete = null }, { askDelete = null },
            )
        }
        QuietButton(if (formOpen) "Скрыть форму" else "+ Создать своё упражнение", { formOpen = !formOpen })
        Reveal(formOpen) {
            Callout {
                LabeledField("Название", name, { name = it }, maxLength = 80, placeholder = "Например, отжимания на брусьях дома")
                Muted("Тип")
                IconTiles(
                    listOf(TileOption("w", "gym", "С весом"), TileOption("bw", "friends", "Без веса"), TileOption("time", "calendar", "На время")),
                    kind, { kind = it; step = if (it == "w") 1.0 else BuiltinData.defaultsFor(it).step },
                )
                if (kind == "w") {
                    Muted("Шаг веса, кг")
                    SegmentedControl(listOf(1.0, 2.5, 5.0).map { it to Js.fmt(it) + " кг" }, step, { step = it })
                }
                LabeledField("Заметка по технике, по желанию", tip, { tip = it }, maxLength = 300, singleLine = false, minLines = 2)
                PrimaryButton("Создать и добавить", {
                    val d = vm.createCustomEx(name, kind, step, tip)
                    if (d != null) { onPick(d); name = ""; tip = ""; formOpen = false }
                })
            }
        }
    }
}
