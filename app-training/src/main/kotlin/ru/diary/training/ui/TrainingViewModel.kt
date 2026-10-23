package ru.diary.training.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.diary.core.contract.DiaryClient
import ru.diary.core.contract.DiaryContract
import ru.diary.core.contract.LinkResult
import ru.diary.core.contract.NutritionDay
import ru.diary.core.contract.NutritionMeta
import ru.diary.core.date.Dates
import ru.diary.core.theme.ThemeMode
import ru.diary.core.util.Js
import ru.diary.training.data.Settings
import ru.diary.training.data.SettingsStore
import ru.diary.training.data.TrainingRepository
import ru.diary.training.domain.ActiveEx
import ru.diary.training.domain.ActiveWorkout
import ru.diary.training.domain.BuiltinData
import ru.diary.training.domain.CustomEx
import ru.diary.training.domain.ExDef
import ru.diary.training.domain.ExerciseCatalog
import ru.diary.training.domain.ImportDraft
import ru.diary.training.domain.ImportMode
import ru.diary.training.domain.Ids
import ru.diary.training.domain.Logic
import ru.diary.training.domain.MaxEntry
import ru.diary.training.domain.Plan
import ru.diary.training.domain.Program
import ru.diary.training.domain.ProgramItem
import ru.diary.training.domain.Session
import ru.diary.training.domain.TrainingData
import ru.diary.training.domain.Transfer
import ru.diary.training.domain.WeekEntry
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Короткое сообщение внизу экрана; `onAction` — кнопка «Отменить». */
data class UiMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** Управление таймером отдыха вне процесса UI: уведомление, вибрация, работа при выключенном экране. */
interface RestController {
    fun start(endAt: Long, label: String)
    fun cancel()
}

object NoRestController : RestController {
    override fun start(endAt: Long, label: String) = Unit
    override fun cancel() = Unit
}

/** Состояние связи с «Рационом». */
sealed interface NutritionLink {
    data object None : NutritionLink
    data class Ready(val meta: NutritionMeta, val days: Map<String, NutritionDay>) : NutritionLink
}

class TrainingViewModel(
    private val repo: TrainingRepository,
    private val settingsStore: SettingsStore,
    private val rest: RestController,
    private val client: DiaryClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val data: StateFlow<TrainingData?> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _today = MutableStateFlow(Dates.today())
    val today: StateFlow<String> = _today.asStateFlow()

    private val _active = MutableStateFlow<ActiveWorkout?>(null)
    val active: StateFlow<ActiveWorkout?> = _active.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** `true`, когда данные и идущая тренировка загружены. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Последнее значение активной тренировки пишется в БД одним потребителем, чтобы порядок сохранялся. */
    private val persist = Channel<ActiveWorkout?>(Channel.CONFLATED)

    private val _nutrition = MutableStateFlow<NutritionLink>(NutritionLink.None)
    /** Связь с «Рационом»: пока он не установлен или недоступен, блоки интеграции скрыты. */
    val nutrition: StateFlow<NutritionLink> = _nutrition.asStateFlow()

    private val _finished = MutableStateFlow<Session?>(null)
    /** Только что сохранённая тренировка для короткого итога. */
    val finished: StateFlow<Session?> = _finished.asStateFlow()
    fun dismissFinished() { _finished.value = null }

    fun refreshNutrition() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val meta = client.nutritionMeta()
                if (meta !is LinkResult.Ok) return@withContext null
                val to = Dates.today()
                val days = client.nutritionDays(Dates.addDays(to, -90), to)
                if (days !is LinkResult.Ok) null else NutritionLink.Ready(meta.value, days.value.associateBy { it.date })
            }
            _nutrition.value = r ?: NutritionLink.None
        }
    }

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            _active.value = repo.activeOnce()
            _ready.value = true
            for (a in persist) if (a == null) repo.clearActive() else repo.saveActive(a)
        }
        // Данные «Рациона» перечитываются при изменении, установке и удалении приложения.
        viewModelScope.launch { client.watch(DiaryContract.Nutrition.PACKAGE, DiaryContract.Nutrition.DAYS_URI).collect { refreshNutrition() } }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                refreshToday()
            }
        }
    }

    fun refreshToday() { _today.value = Dates.today() }

    private fun say(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, actionLabel, onAction))
    }

    private fun mutate(f: (TrainingData) -> TrainingData) { viewModelScope.launch { repo.update(f) } }
    private fun current(): TrainingData? = data.value

    // ---------- настройки ----------

    fun setTheme(m: ThemeMode) { viewModelScope.launch { settingsStore.setTheme(m) } }
    fun setDynamicColor(v: Boolean) { viewModelScope.launch { settingsStore.setDynamicColor(v) } }
    fun setRestAuto(v: Boolean) { viewModelScope.launch { settingsStore.setRestAuto(v) } }
    fun setRestLen(sec: Int) { viewModelScope.launch { settingsStore.setRestLen(sec) } }
    fun markNotificationsAsked() { viewModelScope.launch { settingsStore.setNotificationsAsked() } }

    // ---------- занятия ----------

    /** Быстрая запись кардио, волейбола, игры с друзьями или домашнего занятия. */
    fun logQuick(q: String, form: Logic.QuickForm, label: (Session) -> String) {
        val s = Logic.quickSession(q, form, today.value, clock())
        if (s == null) { say("Введите название занятия"); return }
        mutate { it.copy(sessions = it.sessions + s) }
        say("Записано: ${label(s)}", "Отменить") { removeSessionQuiet(s.id) }
    }

    private fun removeSessionQuiet(id: String) = mutate { d -> d.copy(sessions = d.sessions.filter { it.id != id }) }

    fun removeSession(id: String) {
        val s = current()?.sessions?.firstOrNull { it.id == id } ?: return
        removeSessionQuiet(id)
        say("Запись удалена", "Отменить") { mutate { d -> d.copy(sessions = d.sessions + s) } }
    }

    fun addTrackSet(tid: String, reps: Int) = mutate { d -> d.copy(sessions = Logic.addTrackSet(d.sessions, tid, reps, today.value, clock())) }
    fun undoTrackSet(tid: String) = mutate { d -> d.copy(sessions = Logic.undoTrackSet(d.sessions, tid, today.value)) }

    // ---------- активная тренировка ----------

    fun startWorkout(progId: String?) {
        val d = current() ?: return
        setActive(Logic.startWorkout(d, progId, today.value, clock()))
    }

    private fun setActive(a: ActiveWorkout?) {
        _active.value = a
        persist.trySend(a)
    }

    private fun edit(f: (ActiveWorkout) -> ActiveWorkout) {
        val a = _active.value ?: return
        setActive(f(a))
    }

    fun stepSet(i: Int, j: Int, field: Char, dir: Int) = edit { Logic.stepSet(it, i, j, field, dir) }
    fun inputSet(i: Int, j: Int, field: Char, text: String) = edit { Logic.inputSet(it, i, j, field, text) }
    fun feedback(i: Int, j: Int, fb: String) = edit { Logic.setFeedback(it, i, j, fb) }
    fun addSet(i: Int) = edit { Logic.addSet(it, i) }
    fun toggleWarm(k: Int) = edit { it.copy(warm = it.warm + (k to !(it.warm[k] ?: false))) }

    /** Отметка подхода; при выполнении запускается отдых. Возвращает id упражнения, которое надо раскрыть следом. */
    fun toggleDone(i: Int, j: Int): String? {
        val a = _active.value ?: return null
        val wasDone = a.ex[i].sets[j].done
        var next = Logic.toggleDone(a, i, j)
        var open: String? = null
        if (!wasDone) {
            val ex = next.ex[i]
            val sec = Logic.restSeconds(ex, settings.value.restAuto, settings.value.restLen)
            val end = clock() + sec * 1000L
            next = next.copy(restEnd = end, restLabel = ex.d.name)
            rest.start(end, ex.d.name)
            if (ex.sets.none { !it.done }) open = Logic.nextOpenEx(next)
        }
        setActive(next)
        return open
    }

    fun stopRest() {
        rest.cancel()
        edit { it.copy(restEnd = 0L, restLabel = "") }
    }

    fun addExerciseToWorkout(d: ExDef) {
        val data = current() ?: return
        // Только что созданное упражнение может ещё не дойти из БД, поэтому добавляем его в каталог вручную.
        val custom = if (d.custom && data.customEx.none { it.id == d.id }) data.customEx + CustomEx(d.id, d.name, d.kind, d.step, d.tip) else data.customEx
        val ex = Logic.buildActiveEx(ExerciseCatalog(custom), data.sessions, data.plan, today.value, ProgramItem(d.id, d.sets, d.lo, d.hi))
        edit { Logic.addExercise(it, ex) }
    }

    fun discardWorkout() {
        rest.cancel()
        setActive(null)
        say("Тренировка удалена")
    }

    fun finishWorkout(fin: Logic.Finish) {
        val a = _active.value ?: return
        val s = Logic.finishSession(a, fin, clock())
        rest.cancel()
        mutate { it.copy(sessions = it.sessions + s) }
        setActive(null)
        _finished.value = s
        say("Тренировка сохранена")
    }

    // ---------- каталог, программы ----------

    fun createProgram(title: String): String? {
        val t = title.trim()
        if (t.isEmpty()) { say("Введите название программы"); return null }
        val p = Program("p-" + Ids.uid(), t.take(60), false, BuiltinData.warmGen, emptyList())
        mutate { it.copy(programs = it.programs + p) }
        return p.id
    }

    fun duplicateProgram(id: String) {
        mutate { d ->
            val src = d.programs.firstOrNull { it.id == id } ?: return@mutate d
            d.copy(programs = d.programs + src.copy(id = "p-" + Ids.uid(), title = (src.title + " (копия)").take(60), rot = false))
        }
        say("Копия создана")
    }

    fun updateProgram(id: String, f: (Program) -> Program) =
        mutate { d -> d.copy(programs = d.programs.map { if (it.id == id) f(it) else it }) }

    fun deleteProgram(id: String) {
        mutate { d -> if (d.programs.size <= 1) d else d.copy(programs = d.programs.filter { it.id != id }) }
        say("Программа удалена")
    }

    fun resetProgram(id: String) {
        val orig = BuiltinData.defaultPrograms().firstOrNull { it.id == id } ?: return
        mutate { d -> d.copy(programs = d.programs.map { if (it.id == id) orig else it }) }
        say("Программа возвращена")
    }

    /** Создаёт своё упражнение и возвращает его описание. */
    fun createCustomEx(name: String, kind: String, step: Double, tip: String): ExDef? {
        val n = name.trim()
        if (n.isEmpty()) { say("Введите название упражнения"); return null }
        val k = if (kind in setOf("w", "bw", "time")) kind else "w"
        val st = if (k == "w") (if (step > 0) step else 1.0) else BuiltinData.defaultsFor(k).step
        val c = CustomEx("x-" + Ids.uid(), n.take(80), k, st, tip.take(300))
        mutate { it.copy(customEx = it.customEx + c) }
        val d = BuiltinData.defaultsFor(k)
        return ExDef(c.id, c.name, k, st, d.sets, d.lo, d.hi, c.tip, custom = true)
    }

    fun deleteCustomEx(id: String) {
        mutate { d ->
            d.copy(
                customEx = d.customEx.filter { it.id != id },
                programs = d.programs.map { p -> p.copy(items = p.items.filter { it.id != id }) },
            )
        }
        say("Упражнение удалено")
    }

    // ---------- план ----------

    fun updatePlan(f: (Plan) -> Plan) = mutate { it.copy(plan = f(it.plan)) }

    fun startPlanThisWeek() {
        updatePlan { it.copy(start = Dates.weekStart(today.value)) }
        say("План начат: неделя 1")
    }

    fun clearPlanStart() {
        updatePlan { it.copy(start = "") }
        say("Старт сброшен")
    }

    fun setPlanStart(date: String) {
        if (!Dates.isDate(date)) return
        updatePlan { it.copy(start = Dates.weekStart(date)) }
        say("Старт плана сохранён")
    }

    fun deleteMetric(id: String) {
        mutate { d ->
            d.copy(
                plan = d.plan.copy(
                    metrics = d.plan.metrics.filter { it.id != id },
                    tracks = d.plan.tracks.map { if (it.metric == id) it.copy(metric = "") else it },
                ),
                maxes = d.maxes.filter { it.m != id },
            )
        }
        say("Показатель удалён")
    }

    fun deleteTrack(id: String) {
        updatePlan { p -> p.copy(tracks = p.tracks.filter { it.id != id }) }
        say("Занятие удалено")
    }

    fun saveTemplate(name: String) {
        val d = current() ?: return
        when (val r = Transfer.saveTemplate(d, name)) {
            is Transfer.TemplateSave.Ok -> { mutate { r.data }; say("Шаблон сохранён") }
            Transfer.TemplateSave.NoName -> say("Введите название шаблона")
            Transfer.TemplateSave.LimitReached -> say("Можно хранить до ${Transfer.MAX_TEMPLATES} шаблонов")
        }
    }

    fun applyTemplate(id: String) {
        mutate { d -> Transfer.applyTemplate(d, id) ?: d }
        say("Шаблон применён")
    }

    fun deleteTemplate(id: String) {
        mutate { Transfer.deleteTemplate(it, id) }
        say("Шаблон удалён")
    }

    // ---------- замеры и журнал недели ----------

    fun saveMax(metricId: String, valueText: String, day: String?) {
        val d = current() ?: return
        val m = d.plan.metrics.firstOrNull { it.id == metricId } ?: return
        if (valueText.isBlank() || Js.parseFloat(valueText.replaceFirst(',', '.')).isNaN()) { say("Введите результат"); return }
        val v = Js.parseNum(valueText)
        val prev = Logic.bestOf(d.plan, d.maxes, metricId)
        val rec = prev != null && (if (m.dir > 0) v > prev else v < prev)
        val entry = MaxEntry(Ids.uid(clock()), day ?: today.value, metricId, v)
        mutate { cur ->
            cur.copy(
                maxes = cur.maxes + entry,
                plan = cur.plan.copy(tracks = cur.plan.tracks.map { t ->
                    if (t.kind == "sets" && t.metric == metricId && v >= 1) t.copy(base = Js.clamp(Js.round(v), 1.0, 999.0).toInt()) else t
                }),
            )
        }
        say((if (rec) "Новый рекорд! " else "Замер записан: ") + m.name.lowercase() + ", " + Js.fmt(v) + " " + m.unit)
    }

    fun deleteMax(id: String) {
        mutate { d -> d.copy(maxes = d.maxes.filter { it.id != id }) }
        say("Замер удалён")
    }

    fun setWeek(ws: String, kgText: String? = null, note: String? = null) {
        mutate { d ->
            val e = d.wlog[ws] ?: WeekEntry(0.0, "")
            val ne = WeekEntry(if (kgText != null) Js.parseNum(kgText) else e.kg, if (note != null) note.take(300) else e.note)
            d.copy(wlog = if (ne.kg != 0.0 || ne.note.isNotEmpty()) d.wlog + (ws to ne) else d.wlog - ws)
        }
    }

    // ---------- экспорт, импорт, сброс ----------

    fun exportText(): String? {
        val d = current() ?: return null
        return Transfer.exportText(d, Instant.ofEpochMilli(clock()).truncatedTo(ChronoUnit.MILLIS).toString())
    }

    fun parseImport(text: String): ImportDraft? = Transfer.parseImport(text)

    fun applyImport(draft: ImportDraft, mode: ImportMode) {
        mutate { Transfer.applyImport(it, draft, mode) }
        say("Данные импортированы")
    }

    fun resetAll() {
        viewModelScope.launch { repo.replaceAll(Transfer.reset()) }
        say("Данные удалены")
    }

    fun message(text: String) = say(text)

    class Factory(
        private val repo: TrainingRepository, private val settings: SettingsStore, private val rest: RestController, private val client: DiaryClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TrainingViewModel(repo, settings, rest, client) as T
    }
}

@Suppress("unused")
private fun ActiveEx.label() = d.name
