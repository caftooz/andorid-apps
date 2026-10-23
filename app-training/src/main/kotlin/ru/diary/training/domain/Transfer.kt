package ru.diary.training.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import ru.diary.core.util.Js
import kotlin.math.abs
import kotlin.math.floor

/** Результат разбора файла импорта до применения: что нашли и сколько записей пропущено. */
data class ImportDraft(
    val list: List<Session>,
    val skipped: Int,
    val programs: List<Program>,
    val customEx: List<CustomEx>,
    val plan: Plan?,
    val maxes: List<MaxEntry>?,
    val wlog: Map<String, WeekEntry>?,
    val templates: List<Template>?,
)

enum class ImportMode { Replace, Merge }

/**
 * Экспорт и импорт JSON, совместимые с веб-версией (`buildExport` / `parseImport` / `applyImport`, `version: 3`).
 */
object Transfer {
    const val APP = "dnevnik-trenirovok"
    const val VERSION = 3
    private const val NOTE = "sessions: type = gym | volley | friends; в gym ex[].sets: w (кг), r (повторы или секунды), fb (easy | ok | hard). " +
        "rpe: шкала 1-5. programs[].items: упражнения программы (id, sets, lo-hi повторов). kind: w = вес и повторы, bw = повторы, time = секунды."

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private fun num(d: Double): JsonPrimitive =
        if (d == floor(d) && abs(d) < 1e15) JsonPrimitive(d.toLong()) else JsonPrimitive(d)

    fun initialData(): TrainingData = TrainingData(programs = BuiltinData.defaultPrograms(), plan = BuiltinData.defaultPlan())

    // ---------- сериализация ----------

    fun sessionJson(s: Session): JsonObject = buildJsonObject {
        put("id", s.id)
        put("date", s.date)
        put("type", s.type)
        put("prog", s.prog?.let { JsonPrimitive(it) } ?: JsonNull)
        put("title", s.title)
        put("start", JsonPrimitive(s.start))
        put("min", JsonPrimitive(s.min))
        put("rpe", JsonPrimitive(s.rpe))
        putJsonArray("pain") { s.pain.forEach { add(JsonPrimitive(it)) } }
        put("note", s.note)
        putJsonArray("ex") {
            s.ex.forEach { e ->
                add(buildJsonObject {
                    put("id", e.id)
                    e.name?.let { put("name", it) }
                    e.kind?.let { put("kind", it) }
                    putJsonArray("sets") {
                        e.sets.forEach { st ->
                            add(buildJsonObject {
                                put("w", num(st.w))
                                put("r", JsonPrimitive(st.r))
                                put("fb", st.fb?.let { JsonPrimitive(it) } ?: JsonNull)
                            })
                        }
                    }
                })
            }
        }
        s.tid?.let { put("tid", it) }
        s.kcal?.let { put("kcal", JsonPrimitive(it)) }
        s.sl?.let { sl -> putJsonArray("sl") { sl.forEach { add(JsonPrimitive(it)) } } }
    }

    fun programJson(p: Program, catalog: ExerciseCatalog? = null): JsonObject = buildJsonObject {
        put("id", p.id)
        put("title", p.title)
        put("rot", p.rot)
        putJsonArray("warm") { p.warm.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("items") {
            p.items.forEach { it0 ->
                add(buildJsonObject {
                    put("id", it0.id)
                    if (catalog != null) put("name", catalog.nameOf(it0.id))
                    put("sets", JsonPrimitive(it0.sets))
                    put("lo", JsonPrimitive(it0.lo))
                    put("hi", JsonPrimitive(it0.hi))
                })
            }
        }
    }

    fun planJson(p: Plan): JsonObject = buildJsonObject {
        put("name", p.name)
        put("start", p.start)
        put("protein", p.protein)
        put("volleyHints", p.volleyHints)
        put("deloadEvery", JsonPrimitive(p.deloadEvery))
        put("testEvery", JsonPrimitive(p.testEvery))
        putJsonArray("blocks") {
            p.blocks.forEach { b ->
                add(buildJsonObject { put("id", b.id); put("name", b.name); put("weeks", JsonPrimitive(b.weeks)); put("text", b.text) })
            }
        }
        putJsonArray("days") {
            p.days.forEach { d -> add(buildJsonObject { put("k", d.k); put("prog", d.prog); put("note", d.note) }) }
        }
        putJsonArray("rules") { p.rules.forEach { r -> add(buildJsonObject { put("t", r.t); put("d", r.d) }) } }
        putJsonArray("metrics") {
            p.metrics.forEach { m ->
                add(buildJsonObject {
                    put("id", m.id); put("name", m.name); put("unit", m.unit)
                    put("goal", m.goal?.let { num(it) } ?: JsonNull)
                    put("goalText", m.goalText); put("dir", JsonPrimitive(m.dir)); put("how", m.how)
                })
            }
        }
        putJsonArray("tracks") {
            p.tracks.forEach { t ->
                add(buildJsonObject {
                    put("id", t.id); put("name", t.name); put("kind", t.kind); put("ic", t.ic)
                    put("goal", JsonPrimitive(t.goal)); put("goalText", t.goalText); put("dur", JsonPrimitive(t.dur)); put("intro", t.intro)
                    putJsonArray("steps") { t.steps.forEach { s -> add(buildJsonObject { put("n", s.n); put("t", s.t) }) } }
                    put("cur", JsonPrimitive(t.cur))
                    putJsonArray("items") { t.items.forEach { s -> add(buildJsonObject { put("n", s.n); put("d", s.d); put("t", s.t) }) } }
                    put("metric", t.metric)
                    put("pctLo", JsonPrimitive(t.pctLo)); put("pctHi", JsonPrimitive(t.pctHi))
                    put("dayTarget", JsonPrimitive(t.dayTarget)); put("base", JsonPrimitive(t.base)); put("tip", t.tip)
                })
            }
        }
    }

    private fun customJson(c: CustomEx): JsonObject = buildJsonObject {
        put("id", c.id); put("name", c.name); put("kind", c.kind); put("step", num(c.step)); put("tip", c.tip)
    }

    /** Формат файла экспорта (`buildExport`). `exportedAt` передаётся снаружи, в ISO-формате. */
    fun buildExport(data: TrainingData, exportedAt: String): JsonObject {
        val catalog = ExerciseCatalog(data.customEx)
        return buildJsonObject {
            put("app", APP)
            put("version", JsonPrimitive(VERSION))
            put("exportedAt", exportedAt)
            putJsonObject("goal") { put("perWeek", JsonPrimitive(WEEKLY_GOAL)) }
            put("note", NOTE)
            put("sessions", JsonArray(data.sessions.sortedBy { it.date + it.start }.map(::sessionJson)))
            put("programs", JsonArray(data.programs.map { programJson(it, catalog) }))
            put("customExercises", JsonArray(data.customEx.map(::customJson)))
            put("plan", planJson(data.plan))
            put("maxes", buildJsonArray {
                data.maxes.forEach { m -> add(buildJsonObject { put("id", m.id); put("date", m.date); put("m", m.m); put("v", num(m.v)) }) }
            })
            putJsonObject("weekLog") {
                data.wlog.forEach { (k, e) -> putJsonObject(k) { put("kg", num(e.kg)); put("note", e.note) } }
            }
            put("templates", buildJsonArray {
                data.templates.forEach { t ->
                    add(buildJsonObject {
                        put("id", t.id); put("name", t.name); put("plan", planJson(t.plan))
                        put("programs", JsonArray(t.programs.map { programJson(it) }))
                    })
                }
            })
            put("exercises", buildJsonArray {
                catalog.all.forEach { d ->
                    add(buildJsonObject {
                        put("id", d.id); put("name", d.name); put("kind", d.kind); put("step", num(d.step))
                        put("defaultSets", JsonPrimitive(d.sets))
                        putJsonArray("defaultReps") { add(JsonPrimitive(d.lo)); add(JsonPrimitive(d.hi)) }
                        put("tip", d.tip)
                    })
                }
            })
        }
    }

    fun exportText(data: TrainingData, exportedAt: String): String = pretty.encodeToString(JsonElement.serializer(), buildExport(data, exportedAt))

    // ---------- импорт ----------

    /** Разбор файла (`parseImport`). Возвращает `null`, если это не JSON дневника. */
    fun parseImport(text: String): ImportDraft? {
        val o = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return null
        }
        val isArray = o is JsonArray
        val obj = o as? JsonObject
        val raw: JsonArray = (if (isArray) o as JsonArray else obj?.get("sessions") as? JsonArray) ?: return null
        val list = raw.mapNotNull { Cleaning.cleanSession(it) }
        fun arr(key: String): JsonArray? = obj?.get(key) as? JsonArray
        return ImportDraft(
            list = list,
            skipped = raw.size - list.size,
            programs = arr("programs")?.mapNotNull { Cleaning.cleanProgram(it) } ?: emptyList(),
            customEx = arr("customExercises")?.mapNotNull { Cleaning.cleanCustomEx(it) } ?: emptyList(),
            plan = obj?.get("plan")?.takeIf { Js.truthy(it) }?.let { Cleaning.cleanPlan(it) },
            maxes = arr("maxes")?.mapNotNull { Cleaning.cleanMax(it) },
            wlog = obj?.get("weekLog")?.takeIf { Js.truthy(it) }?.let { Cleaning.cleanWlog(it) },
            templates = arr("templates")?.mapNotNull { Cleaning.cleanTemplate(it) },
        )
    }

    /** Применение импорта (`applyImport`). Режим «объединить» не трогает существующие записи с теми же id. */
    fun applyImport(cur: TrainingData, d: ImportDraft, mode: ImportMode): TrainingData {
        var sessions = cur.sessions
        var programs = cur.programs
        var customEx = cur.customEx
        var plan = cur.plan
        var maxes = cur.maxes
        var wlog = cur.wlog
        var templates = cur.templates
        if (mode == ImportMode.Replace) {
            sessions = d.list
            if (d.programs.isNotEmpty()) {
                programs = d.programs
                customEx = d.customEx
            }
            d.plan?.let { plan = it }
            d.maxes?.let { maxes = it }
            d.wlog?.let { wlog = it }
            d.templates?.let { templates = it }
        } else {
            val have = cur.sessions.mapTo(HashSet()) { it.id }
            sessions = cur.sessions + d.list.filter { it.id !in have }
            val hc = cur.customEx.mapTo(HashSet()) { it.id }
            customEx = cur.customEx + d.customEx.filter { it.id !in hc }
            val hp = cur.programs.mapTo(HashSet()) { it.id }
            programs = cur.programs + d.programs.filter { it.id !in hp }
            d.plan?.let { ip ->
                val hmt = plan.metrics.mapTo(HashSet()) { it.id }
                val htr = plan.tracks.mapTo(HashSet()) { it.id }
                plan = plan.copy(
                    start = if (plan.start.isEmpty() && ip.start.isNotEmpty()) ip.start else plan.start,
                    metrics = plan.metrics + ip.metrics.filter { it.id !in hmt },
                    tracks = plan.tracks + ip.tracks.filter { it.id !in htr },
                )
            }
            d.maxes?.let { im ->
                val hm = cur.maxes.mapTo(HashSet()) { it.id }
                maxes = cur.maxes + im.filter { it.id !in hm }
            }
            d.templates?.let { it0 ->
                val ht = cur.templates.mapTo(HashSet()) { it.id }
                templates = cur.templates + it0.filter { it.id !in ht }
            }
            d.wlog?.let { iw ->
                val m = LinkedHashMap(cur.wlog)
                for ((k, v) in iw) if (m[k] == null) m[k] = v
                wlog = m
            }
        }
        val catalog = ExerciseCatalog(customEx)
        programs = programs.map { p -> p.copy(items = p.items.filter { catalog.defOf(it.id) != null }) }
        return TrainingData(sessions, programs, customEx, plan, maxes, wlog, templates)
    }

    /** Удаление всех данных (`resetAll`): всё возвращается к исходному состоянию. */
    fun reset(): TrainingData = initialData()

    // ---------- шаблоны ----------

    const val MAX_TEMPLATES = 12

    sealed interface TemplateSave {
        data class Ok(val data: TrainingData) : TemplateSave
        data object NoName : TemplateSave
        data object LimitReached : TemplateSave
    }

    /** Сохранить текущий план как шаблон вместе с используемыми программами (`saveTemplate`). */
    fun saveTemplate(data: TrainingData, name: String): TemplateSave {
        val n = name.take(60).trim()
        if (n.isEmpty()) return TemplateSave.NoName
        if (data.templates.size >= MAX_TEMPLATES) return TemplateSave.LimitReached
        val ids = LinkedHashSet<String>()
        val progs = ArrayList<Program>()
        for (d in data.plan.days) {
            if (d.prog.isNotEmpty() && ids.add(d.prog)) data.programs.firstOrNull { it.id == d.prog }?.let { progs.add(it) }
        }
        val t = Template(Ids.uid(), n, data.plan.copy(start = ""), progs.take(12))
        return TemplateSave.Ok(data.copy(templates = data.templates + t))
    }

    fun userTemplate(data: TrainingData, id: String): Template? =
        if (id.startsWith("u:")) data.templates.firstOrNull { "u:" + it.id == id } else null

    /** Шаблон по id: `vb`, `girl`, `blank` или `u:<id>` для своих. */
    fun templateMake(data: TrainingData, id: String): TemplateResult? =
        BuiltinData.builtinTemplate(id) ?: userTemplate(data, id)?.let { TemplateResult(it.plan, it.programs) }

    fun templateName(data: TrainingData, id: String): String =
        BuiltinData.builtinTemplates.firstOrNull { it.id == id }?.name ?: userTemplate(data, id)?.name ?: "шаблон"

    /** Применить шаблон: план заменяется (дата старта сохраняется), одноимённые программы перезаписываются, история остаётся. */
    fun applyTemplate(data: TrainingData, id: String): TrainingData? {
        val tm = templateMake(data, id) ?: return null
        val programs = data.programs.toMutableList()
        for (p in tm.programs) {
            val ix = programs.indexOfFirst { it.id == p.id }
            if (ix >= 0) programs[ix] = p else programs.add(p)
        }
        return data.copy(plan = tm.plan.copy(start = data.plan.start), programs = programs)
    }

    fun deleteTemplate(data: TrainingData, id: String): TrainingData =
        data.copy(templates = data.templates.filter { "u:" + it.id != id })
}
