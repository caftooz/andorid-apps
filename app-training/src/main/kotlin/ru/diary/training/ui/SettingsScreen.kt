package ru.diary.training.ui

import ru.diary.core.ui.SegmentedControl
import ru.diary.core.ui.HeroHeader
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.diary.core.date.Dates
import ru.diary.core.theme.ThemeMode
import ru.diary.core.ui.Callout
import ru.diary.core.update.UpdateSection
import ru.diary.training.container
import ru.diary.core.ui.ChoiceChips
import ru.diary.core.ui.ConfirmBox
import ru.diary.core.ui.DangerButton
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.ScreenTitle
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.ui.SectionTitle
import ru.diary.training.domain.ImportDraft
import ru.diary.training.domain.ImportMode
import ru.diary.training.domain.TrainingData

/** Максимальный размер файла импорта: больше этого дневник породить не может, а защита от случайного огромного файла нужна. */
private const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

/** Вкладка «Настройки»: тема, таймер отдыха, экспорт и импорт JSON, удаление данных. */
@Composable
fun SettingsScreen(vm: TrainingViewModel, data: TrainingData) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf<ImportDraft?>(null) }
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
                    val bytes = input.readNBytesLimited(MAX_IMPORT_BYTES)
                    String(bytes, Charsets.UTF_8)
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
        HeroHeader("Настройки", subtitle = "Тема, отдых, данные и обновления", icon = "sliders")
        SectionTitle("Тема")
        SegmentedControl(listOf(ThemeMode.Light to "Светлая", ThemeMode.Dark to "Тёмная", ThemeMode.System to "Системная"), s.theme, { vm.setTheme(it) })
        Muted("«Системная» следует настройке телефона.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CheckRow("Цвета обоев (Material You) вместо фирменных", s.dynamicColor) { vm.setDynamicColor(it) }
        }

        SectionTitle("Отдых между подходами")
        CheckRow("Автотаймер: брать время отдыха из упражнения", s.restAuto) { vm.setRestAuto(it) }
        Muted("Без автотаймера используется выбранное время (${s.restLen} с). Его можно менять прямо во время тренировки.")

        SectionTitle("Где хранятся данные")
        Muted("Данные хранятся только на этом телефоне и никуда не отправляются. Делайте экспорт, чтобы не потерять историю.")

        SectionTitle("Схема мышц")
        Muted("Контуры фигуры — react-body-highlighter, © 2020 GV79, лицензия MIT. Какие мышцы работают в упражнениях, размечено для этого приложения.")

        SectionTitle("Обновления")
        UpdateSection(ctx.container.updater)

        SectionTitle("Экспорт")
        Muted("Файл JSON с историей занятий, вашими программами, планом и упражнениями. Его можно перенести в веб-версию или обратно сюда.")
        PrimaryButton("Сохранить JSON в файл", { export.launch("trenirovki-${Dates.today()}.json") })

        SectionTitle("Импорт")
        Muted("Выберите файл JSON, экспортированный из этого дневника или из веб-версии.")
        SecondaryButton("Выбрать файл", { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        draft?.let { d ->
            Callout {
                Text(
                    "Найдено занятий: ${d.list.size}, программ: ${d.programs.size}, своих упражнений: ${d.customEx.size}" +
                        (if (d.skipped > 0) ". Пропущено повреждённых: ${d.skipped}" else "") + ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Объединить", { vm.applyImport(d, ImportMode.Merge); draft = null })
                    SecondaryButton("Заменить", { vm.applyImport(d, ImportMode.Replace); draft = null })
                    QuietButton("Отмена", { draft = null })
                }
                Muted("«Объединить» добавляет новые записи и оставляет текущие. «Заменить» стирает текущие занятия, программы и план.")
            }
        }

        SectionTitle("Удалить всё")
        if (!resetAsk) DangerButton("Удалить все данные", { resetAsk = true })
        else ConfirmBox(
            "Вся история занятий будет удалена, программы вернутся к исходным, свои упражнения пропадут. Сначала сделайте экспорт, если хотите всё сохранить.",
            "Да, удалить", { vm.resetAll(); resetAsk = false }, { resetAsk = false },
        )
        Muted("Записей в дневнике: ${data.sessions.size}")
    }
}

private fun java.io.InputStream.readNBytesLimited(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > limit) error("Файл слишком большой")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
