package ru.diary.core.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.diary.core.ui.Callout
import ru.diary.core.ui.MinTouch
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.SecondaryButton

/** Раздел «Обновления» для экрана настроек. */
@Composable
fun UpdateSection(updater: Updater, modifier: Modifier = Modifier) {
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(updater.autoCheck) }
    var needPermission by rememberSaveable { mutableStateOf(false) }

    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted("Установлена версия ${updater.installedVersion}. Приложение ищет новую версию в выпусках GitHub (это единственное обращение к сети) и скачивает её сама; установку подтверждаете вы одним нажатием.")
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(role = Role.Checkbox) { auto = !auto; updater.autoCheck = auto }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(auto, null)
            Text("Проверять обновления при запуске", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        when (val s = state) {
            UpdateState.Idle, is UpdateState.UpToDate -> {
                if (s is UpdateState.UpToDate) Muted("У вас последняя версия.")
                SecondaryButton("Проверить обновления", { scope.launch { updater.check(); (updater.state.value as? UpdateState.Available)?.let { updater.download() } } })
            }
            UpdateState.Checking -> { Muted("Проверяю…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is UpdateState.Available -> {
                Text("Доступна версия ${s.info.version}", style = MaterialTheme.typography.titleSmall)
                PrimaryButton("Скачать", { scope.launch { updater.download() } })
            }
            is UpdateState.Downloading -> {
                Text("Скачиваю версию ${s.info.version}: ${s.percent}%", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.Ready -> {
                Text("Версия ${s.info.version} скачана и проверена", style = MaterialTheme.typography.titleSmall)
                if (s.info.notes.isNotBlank()) Muted(s.info.notes)
                PrimaryButton("Установить", { needPermission = !updater.install() })
                if (needPermission) Callout { Text("Разрешите этому приложению установку обновлений на открывшемся экране, вернитесь назад и нажмите «Установить» ещё раз.", style = MaterialTheme.typography.bodySmall) }
            }
            is UpdateState.Installing -> Muted("Установка версии ${s.info.version}… подтвердите её в окне системы.")
            is UpdateState.Failed -> {
                Callout { Text(s.message, style = MaterialTheme.typography.bodyMedium) }
                SecondaryButton("Проверить снова", { updater.dismissError(); scope.launch { updater.check(); (updater.state.value as? UpdateState.Available)?.let { updater.download() } } })
            }
        }
    }
}

/**
 * Предложение установить уже скачанную и проверенную версию. Показывается один раз за версию;
 * `enabled = false` откладывает диалог, например во время тренировки.
 */
@Composable
fun UpdatePrompt(updater: Updater, enabled: Boolean = true) {
    val state by updater.state.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    var needPermission by rememberSaveable { mutableStateOf(false) }
    val ready = state as? UpdateState.Ready
    LaunchedEffect(Unit) { Updater.installErrors.collect { /* ошибки показываются в разделе «Обновления» */ } }
    if (ready != null && enabled && dismissed != ready.info.version) {
        AlertDialog(
            onDismissRequest = { dismissed = ready.info.version },
            title = { Text("Доступно обновление ${ready.info.version}") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Новая версия скачана и проверена. Установить сейчас? Данные сохранятся.")
                    if (needPermission) Text("Разрешите установку на открывшемся экране, вернитесь и нажмите «Установить» ещё раз.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ if (updater.install()) dismissed = ready.info.version else needPermission = true }) { Text("Установить") } },
            dismissButton = { QuietButton("Позже", { dismissed = ready.info.version }) },
        )
    }
}
