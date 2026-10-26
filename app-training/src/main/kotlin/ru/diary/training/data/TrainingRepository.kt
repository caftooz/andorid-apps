package ru.diary.training.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.put
import ru.diary.training.ui.ProgramLetters
import ru.diary.training.domain.ActiveWorkout
import ru.diary.training.domain.BuiltinData
import ru.diary.training.domain.Cleaning
import ru.diary.training.domain.CustomEx
import ru.diary.training.domain.MaxEntry
import ru.diary.training.domain.Plan
import ru.diary.training.domain.Program
import ru.diary.training.domain.Session
import ru.diary.training.domain.Template
import ru.diary.training.domain.TrainingData
import ru.diary.training.domain.Transfer
import ru.diary.training.domain.WeekEntry

/**
 * Хранилище дневника тренировок поверх Room. Наружу отдаёт целое состояние (`TrainingData`),
 * изменения принимает как чистую функцию `update { данные -> новые данные }` и записывает только то, что изменилось.
 */
class TrainingRepository(private val db: TrainingDatabase, private val onChanged: () -> Unit = {}) {
    private val dao = db.dao()
    private val writeLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    // Каждая таблица разбирается отдельно: правка плана не заставляет заново разбирать сотни записей истории.
    private fun parse(text: String) = json.parseToJsonElement(text)

    val data: Flow<TrainingData> = combine(
        dao.sessions().map { l -> l.mapNotNull { Cleaning.cleanSession(parse(it.json)) } },
        // Программы всегда по алфавиту (буква, затем название): так их видят главная, план, очередь и виджет.
        dao.programs().map { l -> ProgramLetters.sorted(l.mapNotNull { Cleaning.cleanProgram(parse(it.json)) }) },
        dao.customEx().map { l -> l.mapNotNull { Cleaning.cleanCustomEx(parse(it.json)) } },
        dao.plan().map { e -> e?.let { Cleaning.cleanPlan(parse(it.json)) } ?: BuiltinData.defaultPlan() },
        dao.maxes().map { l -> l.map { MaxEntry(it.id, it.date, it.m, it.v) } },
        dao.weekLog().map { l -> LinkedHashMap<String, WeekEntry>().also { m -> l.forEach { m[it.weekStart] = WeekEntry(it.kg, it.note) } } as Map<String, WeekEntry> },
        dao.templates().map { l -> l.mapNotNull { Cleaning.cleanTemplate(parse(it.json)) } },
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        TrainingData(
            arr[0] as List<Session>, arr[1] as List<Program>, arr[2] as List<CustomEx>, arr[3] as Plan,
            arr[4] as List<MaxEntry>, arr[5] as Map<String, WeekEntry>, arr[6] as List<Template>,
        )
    }

    /** Идущая тренировка или `null`. */
    val active: Flow<ActiveWorkout?> = dao.active().map { e -> e?.let { runCatching { json.decodeFromString<ActiveWorkout>(it.json) }.getOrNull() } }

    suspend fun activeOnce(): ActiveWorkout? =
        dao.activeOnce()?.let { runCatching { json.decodeFromString<ActiveWorkout>(it.json) }.getOrNull() }

    suspend fun saveActive(a: ActiveWorkout) = dao.putActive(ActiveWorkoutEntity(json = json.encodeToString(a)))
    suspend fun clearActive() = dao.clearActive()

    /** При первом запуске кладёт в базу исходные программы и план из прототипа. */
    suspend fun ensureSeeded() = writeLock.withLock {
        if (dao.plan().first() == null) {
            val init = Transfer.initialData()
            writeAll(init)
        }
    }

    /** Применить изменение: читает текущее состояние, считает новое и записывает разницу одной операцией. */
    suspend fun update(transform: (TrainingData) -> TrainingData) = writeLock.withLock {
        val old = data.first()
        val new = transform(old)
        if (new != old) { writeDiff(old, new); onChanged() }
    }

    /** Полная замена данных (импорт «заменить», сброс). */
    suspend fun replaceAll(new: TrainingData) = writeLock.withLock { writeAll(new); onChanged() }

    private suspend fun writeAll(d: TrainingData) {
        dao.replaceAll(
            d.sessions.map(::sessionEntity), programEntities(d.programs), customEntities(d.customEx), planEntity(d.plan),
            d.maxes.map { MaxEntity(it.id, it.date, it.m, it.v) }, d.wlog.map { (k, v) -> WeekLogEntity(k, v.kg, v.note) },
            templateEntities(d.templates),
        )
    }

    private suspend fun writeDiff(old: TrainingData, new: TrainingData) {
        if (new.sessions != old.sessions) {
            val oldById = old.sessions.associateBy { it.id }
            val newIds = new.sessions.mapTo(HashSet()) { it.id }
            old.sessions.filter { it.id !in newIds }.forEach { dao.deleteSession(it.id) }
            dao.upsertSessions(new.sessions.filter { oldById[it.id] != it }.map(::sessionEntity))
        }
        if (new.programs != old.programs) { dao.clearPrograms(); dao.upsertPrograms(programEntities(new.programs)) }
        if (new.customEx != old.customEx) { dao.clearCustomEx(); dao.upsertCustomEx(customEntities(new.customEx)) }
        if (new.plan != old.plan) dao.upsertPlan(planEntity(new.plan))
        if (new.maxes != old.maxes) {
            val newIds = new.maxes.mapTo(HashSet()) { it.id }
            old.maxes.filter { it.id !in newIds }.forEach { dao.deleteMax(it.id) }
            dao.upsertMaxes(new.maxes.map { MaxEntity(it.id, it.date, it.m, it.v) })
        }
        if (new.wlog != old.wlog) {
            old.wlog.keys.filter { it !in new.wlog }.forEach { dao.deleteWeek(it) }
            dao.upsertWeek(new.wlog.map { (k, v) -> WeekLogEntity(k, v.kg, v.note) })
        }
        if (new.templates != old.templates) { dao.clearTemplates(); dao.upsertTemplates(templateEntities(new.templates)) }
    }

    private fun sessionEntity(s: Session) = SessionEntity(
        s.id, s.date, s.type, s.start, s.min, s.kcal ?: 0, Transfer.sessionJson(s).toString(),
    )
    private fun programEntities(l: List<Program>) = l.mapIndexed { i, p -> ProgramEntity(p.id, i, Transfer.programJson(p).toString()) }
    private fun customEntities(l: List<CustomEx>) = l.mapIndexed { i, c -> CustomExEntity(c.id, i, Transfer.customJson(c).toString()) }
    private fun planEntity(p: Plan) = PlanEntity(json = Transfer.planJson(p).toString())
    private fun templateEntities(l: List<Template>) = l.mapIndexed { i, t ->
        TemplateEntity(t.id, i, buildJsonObject {
            put("id", t.id); put("name", t.name); put("plan", Transfer.planJson(t.plan))
            put("programs", JsonArray(t.programs.map { Transfer.programJson(it) }))
        }.toString())
    }
}
