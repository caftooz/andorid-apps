package ru.diary.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.diary.core.contract.DiaryClient
import ru.diary.core.contract.DiaryContract
import ru.diary.core.contract.LinkResult
import ru.diary.core.contract.TrainingDay
import ru.diary.core.contract.TrainingMeta
import ru.diary.core.date.Dates
import ru.diary.core.theme.ThemeMode
import ru.diary.nutrition.data.NutritionRepository
import ru.diary.nutrition.data.Settings
import ru.diary.nutrition.data.SettingsStore
import ru.diary.nutrition.domain.Dish
import ru.diary.nutrition.domain.Entry
import ru.diary.nutrition.domain.NutritionData
import ru.diary.nutrition.domain.NutritionLogic
import ru.diary.nutrition.domain.NutritionTransfer
import ru.diary.core.util.Js
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class UiMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** Состояние связи с «Тренировками». Без них интерфейс не показывает интеграционные блоки. */
sealed interface TrainingLink {
    data object None : TrainingLink
    data class Ready(val meta: TrainingMeta, val days: Map<String, TrainingDay>) : TrainingLink
}

class NutritionViewModel(
    private val repo: NutritionRepository,
    private val settingsStore: SettingsStore,
    private val client: DiaryClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val data: StateFlow<NutritionData?> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _today = MutableStateFlow(Dates.today())
    val today: StateFlow<String> = _today.asStateFlow()

    private val _link = MutableStateFlow<TrainingLink>(TrainingLink.None)
    val link: StateFlow<TrainingLink> = _link.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            while (true) { delay(30_000); _today.value = Dates.today() }
        }
        // Данные «Тренировок» перечитываются при изменении, установке и удалении приложения.
        viewModelScope.launch {
            client.watch(DiaryContract.Training.PACKAGE, DiaryContract.Training.DAYS_URI).collect { refreshLink() }
        }
    }

    fun refreshToday() { _today.value = Dates.today() }

    fun refreshLink() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val to = Dates.today()
                val meta = client.trainingMeta()
                if (meta !is LinkResult.Ok) return@withContext null
                val days = client.trainingDays(Dates.addDays(to, -27), to)
                if (days !is LinkResult.Ok) null else TrainingLink.Ready(meta.value, days.value.associateBy { it.date })
            }
            _link.value = result ?: TrainingLink.None
        }
    }

    private fun say(text: String, label: String? = null, action: (() -> Unit)? = null) { _messages.tryEmit(UiMessage(text, label, action)) }
    private fun mutate(f: (NutritionData) -> NutritionData) { viewModelScope.launch { repo.update(f) } }

    private fun nowTime(): String = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

    fun setTheme(m: ThemeMode) { viewModelScope.launch { settingsStore.setTheme(m) } }
    fun setDynamicColor(v: Boolean) { viewModelScope.launch { settingsStore.setDynamicColor(v) } }

    fun eat(d: Dish, date: String, portion: Double) {
        val e = NutritionLogic.eat(d, date, nowTime(), portion, clock())
        mutate { it.copy(log = it.log + e) }
        say("Записано: ${d.name} · ${Dates.label(date, today.value).lowercase()}", "Отменить") { removeQuiet(e.id) }
    }

    fun saveOutOfPlan(name: String, kcal: String, protein: String, date: String) {
        val e = NutritionLogic.outOfPlan(name, kcal, protein, date, nowTime(), clock())
        mutate { it.copy(log = it.log + e) }
        say("Записано вне плана", "Отменить") { removeQuiet(e.id) }
    }

    private fun removeQuiet(id: String) = mutate { d -> d.copy(log = d.log.filter { it.id != id }) }

    fun removeEntry(e: Entry) {
        removeQuiet(e.id)
        say("Запись удалена", "Отменить") { mutate { d -> d.copy(log = d.log + e) } }
    }

    fun addDish(name: String, cat: String, kcal: String, protein: String, notes: String): Boolean {
        val c = NutritionLogic.newDish(name, cat, kcal, protein, notes)
        if (c == null) { say("Введите название блюда"); return false }
        mutate { it.copy(custom = it.custom + c) }
        say("Блюдо добавлено")
        return true
    }

    fun deleteDish(id: String) = mutate { d -> d.copy(custom = d.custom.filter { it.id != id }) }

    fun exportText(): String? = data.value?.let {
        NutritionTransfer.exportText(it, Instant.ofEpochMilli(clock()).truncatedTo(ChronoUnit.MILLIS).toString())
    }

    fun parseImport(text: String) = NutritionTransfer.parseImport(text)

    fun applyImport(d: NutritionTransfer.Draft, mode: NutritionTransfer.Mode) {
        mutate { NutritionTransfer.applyImport(it, d, mode) }
        say("Данные импортированы")
    }

    fun resetAll() {
        viewModelScope.launch { repo.replaceAll(NutritionData()) }
        say("Данные удалены")
    }

    fun message(text: String) = say(text)

    @Suppress("unused")
    private fun fmt(d: Double) = Js.fmt(d)

    class Factory(private val repo: NutritionRepository, private val settings: SettingsStore, private val client: DiaryClient) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NutritionViewModel(repo, settings, client) as T
    }
}
