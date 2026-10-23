package ru.diary.training.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ru.diary.core.date.Dates
import ru.diary.core.util.Js

/**
 * Очистка входных данных. Прямой перенос функций `clean*` и `migrateSession` из `trenirovki.html`:
 * любое значение приводится к допустимому, а совсем повреждённые записи отбрасываются (возвращают `null`).
 */
object Cleaning {
    val TYPE_NAMES = mapOf("gym" to "Зал", "volley" to "Волейбол", "friends" to "Игра с друзьями", "other" to "Кардио / другое")
    val FB = setOf("easy", "ok", "hard")
    val KINDS = setOf("w", "bw", "time")
    val DAY_KINDS = setOf("gym", "home", "vb", "rest")
    val ICONS = setOf("gym", "vb", "home", "rest", "cardio", "friends", "flame", "stretch", "star", "target", "check", "plus")
    private val LEGACY_TRACKS = setOf("handstand", "stretch", "pullday")

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject?.f(key: String): JsonElement? = this?.get(key)

    private fun int(e: JsonElement?, fallback: Double, lo: Int, hi: Int): Int =
        Js.clamp(Js.round(Js.numOr(e, fallback)), lo.toDouble(), hi.toDouble()).toInt()

    private fun strOr(e: JsonElement?, fallback: String, n: Int): String =
        if (Js.truthy(e)) Js.str(e, n) else fallback.take(n)

    private fun items(e: JsonElement?): List<JsonElement> = (e as? JsonArray)?.toList() ?: emptyList()

    fun cleanSession(raw: JsonElement?): Session? {
        val s = raw.obj() ?: return null
        // migrateSession: handstand | stretch | pullday -> track
        var type = Js.str(s["type"])
        var tidRaw: JsonElement? = s["tid"]
        if (type in LEGACY_TRACKS) {
            tidRaw = s["type"]
            type = "track"
        }
        val date = Js.str(if (Js.truthy(s["date"])) s["date"] else null)
        if (!Dates.isDate(date)) return null
        val effType = if (type in TYPE_NAMES) type else if (type == "track") "track" else "gym"
        val kc = Js.round(Js.parseNum(s["kcal"])).toInt()
        val ex = items(s["ex"]).mapNotNull { e ->
            val eo = e.obj() ?: return@mapNotNull null
            if (!Js.truthy(eo["id"])) return@mapNotNull null
            val setsArr = eo["sets"] as? JsonArray ?: return@mapNotNull null
            val sets = setsArr.map { x ->
                val xo = x.obj()
                val fbv = xo?.get("fb")?.let { Js.str(it) }
                SetResult(
                    w = Js.parseNum(xo.f("w")),
                    r = Js.round(Js.parseNum(xo.f("r"))).toInt(),
                    fb = if (fbv != null && fbv in FB) fbv else null,
                )
            }
            if (sets.isEmpty()) return@mapNotNull null
            val kindStr = Js.str(eo["kind"])
            ExResult(
                id = Js.str(eo["id"]),
                name = if (Js.truthy(eo["name"])) Js.str(eo["name"], 80) else null,
                kind = if (kindStr in KINDS) kindStr else null,
                sets = sets,
            )
        }
        val start = Js.toNumber(s["start"]).let { if (it > 0) it.toLong() else 0L }
        return Session(
            id = if (Js.truthy(s["id"])) Js.str(s["id"]) else Ids.uid(),
            date = date,
            type = effType,
            prog = if (Js.truthy(s["prog"])) Js.str(s["prog"], 40) else null,
            title = Js.str(if (Js.truthy(s["title"])) s["title"] else null, 60),
            start = start,
            min = Js.round(Js.parseNum(s["min"])).toInt(),
            rpe = int(s["rpe"], 0.0, 0, 5),
            pain = items(s["pain"]).map { Js.str(it, 30) }.take(6),
            note = Js.str(if (Js.truthy(s["note"])) s["note"] else null, 1000),
            ex = ex,
            tid = if (Js.truthy(tidRaw)) Js.str(tidRaw, 40) else null,
            kcal = if (kc > 0) kc else null,
            sl = (s["sl"] as? JsonArray)?.map { int(it, 0.0, 0, 999) }?.take(40),
        )
    }

    fun cleanProgram(raw: JsonElement?): Program? {
        val p = raw.obj() ?: return null
        if (!Js.truthy(p["id"])) return null
        val list = items(p["items"]).mapNotNull { it ->
            val o = it.obj() ?: return@mapNotNull null
            if (!Js.truthy(o["id"])) return@mapNotNull null
            val lo = int(o["lo"], 8.0, 1, 999)
            var hi = Js.clamp(Js.round(Js.numOr(o["hi"], lo.toDouble())), 1.0, 999.0).toInt()
            if (hi < lo) hi = lo
            ProgramItem(Js.str(o["id"]), int(o["sets"], 3.0, 1, 10), lo, hi)
        }
        return Program(
            id = Js.str(p["id"], 40),
            title = strOr(p["title"], "Программа", 60),
            rot = Js.truthy(p["rot"]),
            warm = items(p["warm"]).map { Js.str(it, 120) }.filter { it.isNotEmpty() }.take(12),
            items = list.take(20),
        )
    }

    fun cleanCustomEx(raw: JsonElement?): CustomEx? {
        val c = raw.obj() ?: return null
        if (!Js.truthy(c["id"]) || !Js.truthy(c["name"])) return null
        val kindStr = Js.str(c["kind"])
        val kind = if (kindStr in KINDS) kindStr else "w"
        val stepRaw = Js.toNumber(c["step"])
        val step = if (stepRaw > 0) minOf(50.0, stepRaw) else BuiltinData.defaultsFor(kind).step
        return CustomEx(Js.str(c["id"], 40), Js.str(c["name"], 80), kind, step, Js.str(if (Js.truthy(c["tip"])) c["tip"] else null, 300))
    }

    fun cleanMax(raw: JsonElement?): MaxEntry? {
        val x = raw.obj() ?: return null
        val date = Js.str(if (Js.truthy(x["date"])) x["date"] else null)
        if (!Dates.isDate(date) || !Js.truthy(x["m"])) return null
        return MaxEntry(
            id = if (Js.truthy(x["id"])) Js.str(x["id"], 40) else Ids.uid(),
            date = date,
            m = Js.str(x["m"], 40),
            v = Js.parseNum(x["v"]),
        )
    }

    fun cleanWlog(raw: JsonElement?): Map<String, WeekEntry> {
        val out = LinkedHashMap<String, WeekEntry>()
        val o = raw.obj() ?: return out
        for ((k, v) in o) {
            if (!Dates.isDate(k)) continue
            val e = v.obj()
            val kg = Js.parseNum(e.f("kg"))
            val note = Js.str(e.f("note"), 300)
            if (kg != 0.0 || note.isNotEmpty()) out[k] = WeekEntry(kg, note)
        }
        return out
    }

    fun cleanTemplate(raw: JsonElement?): Template? {
        val t = raw.obj() ?: return null
        val planEl = t["plan"]
        if (planEl !is JsonObject && planEl !is JsonArray) return null
        val pl = cleanPlan(planEl).copy(start = "")
        return Template(
            id = if (Js.truthy(t["id"])) Js.str(t["id"], 40) else Ids.uid(),
            name = strOr(t["name"], "Мой шаблон", 60),
            plan = pl,
            programs = items(t["programs"]).mapNotNull { cleanProgram(it) }.take(12),
        )
    }

    fun cleanPlan(raw: JsonElement?): Plan {
        val p = raw.obj()
        if (p == null) {
            // Массивы в JS тоже «объекты», но без блоков и дней: устаревший формат.
            return if (raw is JsonArray) legacyPlan(null) else BuiltinData.defaultPlan()
        }
        val legacy = p["blocks"] !is JsonArray && p["days"] !is JsonArray
        if (legacy) return legacyPlan(p)

        val startStr = Js.str(if (Js.truthy(p["start"])) p["start"] else null)
        val blocks = items(p["blocks"]).take(12).mapIndexed { i, b ->
            val bo = b.obj()
            Block(
                id = strOr(bo.f("id"), "b${i + 1}${Ids.uid()}", 40),
                name = strOr(bo.f("name"), "Блок", 40),
                weeks = int(bo.f("weeks"), 1.0, 1, 104),
                text = Js.str(bo.f("text"), 300),
            )
        }.ifEmpty { listOf(Block("b1", "Основной блок", 12, "")) }
        val dayList = p["days"] as? JsonArray
        val days = (0 until 7).map { i ->
            val d = dayList?.getOrNull(i)?.takeIf { Js.truthy(it) }.obj()
            val k = Js.str(d.f("k"))
            DayPlan(if (k in DAY_KINDS) k else "rest", Js.str(d.f("prog"), 40), Js.str(d.f("note"), 120))
        }
        val rules = items(p["rules"]).take(10).map { r ->
            val ro = r.obj()
            Rule(Js.str(ro.f("t"), 60), Js.str(ro.f("d"), 200))
        }.filter { it.t.isNotEmpty() || it.d.isNotEmpty() }
        val metrics = items(p["metrics"]).take(20).map { m ->
            val mo = m.obj()
            val goalEl = mo.f("goal")
            val noGoal = goalEl == null || goalEl is kotlinx.serialization.json.JsonNull || (goalEl is kotlinx.serialization.json.JsonPrimitive && goalEl.isString && goalEl.content.isEmpty())
            Metric(
                id = strOr(mo.f("id"), "m-${Ids.uid()}", 40),
                name = strOr(mo.f("name"), "Показатель", 60),
                unit = strOr(mo.f("unit"), "раз", 20),
                goal = if (noGoal) null else Js.parseNum(goalEl),
                goalText = Js.str(mo.f("goalText"), 80),
                dir = if (Js.toNumber(mo.f("dir")) < 0) -1 else 1,
                how = Js.str(mo.f("how"), 200),
            )
        }
        val tracks = items(p["tracks"]).take(8).map { t -> cleanTrack(t.obj()) }
        return Plan(
            name = strOr(p["name"], "План", 60),
            start = if (Dates.isDate(startStr)) startStr else "",
            protein = Js.truthy(p["protein"]),
            volleyHints = Js.truthy(p["volleyHints"]),
            deloadEvery = int(p["deloadEvery"], 0.0, 0, 20),
            testEvery = int(p["testEvery"], 0.0, 0, 20),
            blocks = blocks, days = days, rules = rules, metrics = metrics, tracks = tracks,
        )
    }

    private fun cleanTrack(t: JsonObject?): Track {
        val id = Js.str(t.f("id"))
        val kind = if (Js.str(t.f("kind")) == "sets") "sets" else "timed"
        val icRaw = Js.str(t.f("ic"))
        val ic = if (icRaw in ICONS) icRaw else if (id == "stretch") "stretch" else if (kind == "sets") "gym" else "star"
        return Track(
            id = strOr(t.f("id"), "t-${Ids.uid()}", 40),
            name = strOr(t.f("name"), "Занятие", 60),
            kind = kind,
            ic = ic,
            goal = int(t.f("goal"), 0.0, 0, 7),
            goalText = Js.str(t.f("goalText"), 20),
            dur = int(t.f("dur"), 20.0, 1, 600),
            intro = Js.str(t.f("intro"), 600),
            steps = items(t.f("steps")).take(12).map { s ->
                val so = s.obj()
                TrackStep(Js.str(so.f("n"), 80), Js.str(so.f("t"), 300))
            }.filter { it.n.isNotEmpty() },
            cur = int(t.f("cur"), 0.0, 0, 12),
            items = items(t.f("items")).take(20).map { s ->
                val so = s.obj()
                TrackItem(Js.str(so.f("n"), 80), Js.str(so.f("d"), 80), Js.str(so.f("t"), 200))
            }.filter { it.n.isNotEmpty() },
            metric = Js.str(t.f("metric"), 40),
            pctLo = int(t.f("pctLo"), 40.0, 5, 100),
            pctHi = int(t.f("pctHi"), 50.0, 5, 100),
            dayTarget = int(t.f("dayTarget"), 8.0, 1, 30),
            base = int(t.f("base"), 10.0, 1, 999),
            tip = Js.str(t.f("tip"), 500),
        )
    }

    /** Старый формат плана (поля `start`, `hs`, `pmax`): возвращаем план по умолчанию с подстановкой этих полей. */
    private fun legacyPlan(p: JsonObject?): Plan {
        val def = BuiltinData.defaultPlan()
        val startStr = Js.str(if (p != null && Js.truthy(p["start"])) p["start"] else null)
        val hs = Js.toNumber(p.f("hs"))
        val pmax = Js.toNumber(p.f("pmax"))
        return def.copy(
            start = if (Dates.isDate(startStr)) startStr else "",
            tracks = def.tracks.map { t ->
                when {
                    t.id == "handstand" && hs >= 1 -> t.copy(cur = Js.clamp(Js.round(hs), 1.0, 5.0).toInt())
                    t.id == "pullday" && pmax >= 1 -> t.copy(base = Js.clamp(Js.round(pmax), 1.0, 60.0).toInt())
                    else -> t
                }
            },
        )
    }
}
