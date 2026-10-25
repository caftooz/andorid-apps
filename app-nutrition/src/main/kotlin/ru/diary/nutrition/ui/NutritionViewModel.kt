package ru.diary.nutrition.ui

import ru.diary.nutrition.domain.Weigh
import ru.diary.nutrition.domain.WeighItem
import ru.diary.nutrition.domain.Goals
import ru.diary.nutrition.data.NormField
import ru.diary.nutrition.domain.Products
import ru.diary.nutrition.domain.Product
import ru.diary.nutrition.domain.OffResult
import ru.diary.nutrition.data.ProductLookup
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

/** Состояние сканирования штрих-кода на вкладке «Блюда». */
sealed interface ScanState {
    data object Idle : ScanState
    data class Looking(val code: String) : ScanState
    /** Продукт известен: осталось выбрать граммы. `saved` — он уже в «Моих продуктах». */
    data class Ready(val product: Product, val saved: Boolean) : ScanState
    /** Нужно ввести КБЖУ с этикетки; `draft` — то, что удалось найти (например, только название). */
    data class Manual(val code: String, val draft: Product?, val note: String) : ScanState
}

class NutritionViewModel(
    private val repo: NutritionRepository,
    private val settingsStore: SettingsStore,
    private val client: DiaryClient,
    private val lookup: ProductLookup,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val data: StateFlow<NutritionData?> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _today = MutableStateFlow(Dates.today())
    val today: StateFlow<String> = _today.asStateFlow()

    private val _link = MutableStateFlow<TrainingLink>(TrainingLink.None)
    val link: StateFlow<TrainingLink> = _link.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            while (true) { delay(30_000); _today.value = Dates.today() }
        }
        // Нормы из настроек нужны и логике (подсказки недели), и провайдеру.
        viewModelScope.launch {
            settings.collect { s -> Goals.kcal = s.norms.kcal; Goals.proteinMin = s.norms.protein; Goals.proteinMax = maxOf(Goals.proteinMax, s.norms.protein); Goals.fat = s.norms.fat; Goals.carbs = s.norms.carbs; Goals.fiber = s.norms.fiber }
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

    fun setNorm(f: NormField, v: Int) { viewModelScope.launch { settingsStore.setNorm(f, v) } }

    /** «Взвесить»: продукт или однородное блюдо, пищевая ценность пересчитывается на граммы. */
    fun eatWeighed(item: WeighItem, grams: Int, date: String) {
        if (grams !in 1..5000) { say("Укажите вес от 1 до 5000 г"); return }
        val e = Weigh.eat(item, grams, date, nowTime(), clock())
        mutate { d ->
            val prods = item.product?.let { p -> listOf(p.copy(updated = clock())) + d.products.filter { it.code != p.code } } ?: d.products
            d.copy(log = d.log + e, products = prods)
        }
        say("Записано: ${item.name}, $grams г · ${Dates.label(date, today.value).lowercase()}", "Отменить") { removeQuiet(e.id) }
    }

    fun setOnlineLookup(v: Boolean) { viewModelScope.launch { settingsStore.setOnlineLookup(v) } }

    /** Штрих-код считан камерой или введён вручную: ищем у себя, затем (если разрешено) в Open Food Facts. */
    fun onBarcode(raw: String) {
        val code = Products.normalize(raw)
        if (code == null) { say("Не похоже на штрих-код продукта: нужны 8, 12, 13 или 14 цифр"); return }
        data.value?.products?.firstOrNull { it.code == code }?.let { _scan.value = ScanState.Ready(it, saved = true); return }
        if (!settings.value.onlineLookup) {
            _scan.value = ScanState.Manual(code, null, "Поиск в интернете выключен в настройках. Перепишите КБЖУ с этикетки — в следующий раз продукт найдётся сразу.")
            return
        }
        _scan.value = ScanState.Looking(code)
        viewModelScope.launch {
            val r = lookup.find(code, clock())
            if ((_scan.value as? ScanState.Looking)?.code != code) return@launch
            _scan.value = when (r) {
                is ProductLookup.Lookup.Failed -> ScanState.Manual(code, null, "Не удалось связаться с базой продуктов (${r.reason}). Можно ввести КБЖУ с этикетки.")
                is ProductLookup.Lookup.Done -> when (val res = r.result) {
                    OffResult.NotFound -> ScanState.Manual(code, null, "Этого продукта нет в базе. Перепишите КБЖУ с этикетки — он сохранится в «Моих продуктах».")
                    is OffResult.Found -> if (res.complete) ScanState.Ready(res.product, saved = false)
                    else ScanState.Manual(code, res.product, "В базе нет части данных. Проверьте и дополните их по этикетке.")
                }
            }
        }
    }

    fun openProduct(p: Product) { _scan.value = ScanState.Ready(p, saved = true) }
    fun editProduct(p: Product) { _scan.value = ScanState.Manual(p.code, p, "Исправьте данные по этикетке. Значения — на 100 г.") }
    fun cancelScan() { _scan.value = ScanState.Idle }

    /** Сохраняет продукт из формы и сразу предлагает выбрать граммы. */
    fun saveManualProduct(code: String, name: String, brand: String, kcal: String, p: String, f: String, c: String, pack: String, fiber: String = ""): Boolean {
        val prod = Products.manual(code, name, brand, kcal, p, f, c, pack, clock(), fiber)
        if (prod == null) { say("Нужны название и калорийность на 100 г (не больше 1000 ккал)"); return false }
        mutate { d -> d.copy(products = listOf(prod) + d.products.filter { it.code != code }) }
        _scan.value = ScanState.Ready(prod, saved = true)
        return true
    }

    /** «Съел»: запись в дневник; продукт из базы при этом запоминается в «Моих продуктах». */
    /** `closePanel = false`, когда панель закрывается сама с анимацией. */
    fun eatProduct(p: Product, grams: Int, date: String, closePanel: Boolean = true) {
        if (grams !in 1..5000) { say("Укажите вес от 1 до 5000 г"); return }
        val e = Products.eat(p, grams, date, nowTime(), clock())
        val stamped = p.copy(updated = clock())
        mutate { d -> d.copy(log = d.log + e, products = listOf(stamped) + d.products.filter { it.code != p.code }) }
        if (closePanel) _scan.value = ScanState.Idle
        say("Записано: ${p.name}, $grams г · ${Dates.label(date, today.value).lowercase()}", "Отменить") { removeQuiet(e.id) }
    }

    fun deleteProduct(code: String) {
        mutate { d -> d.copy(products = d.products.filter { it.code != code }) }
        _scan.value = ScanState.Idle
        say("Продукт удалён из «Моих продуктов». Записи в дневнике остались.")
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

    class Factory(
        private val repo: NutritionRepository, private val settings: SettingsStore, private val client: DiaryClient, private val lookup: ProductLookup,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NutritionViewModel(repo, settings, client, lookup) as T
    }
}
