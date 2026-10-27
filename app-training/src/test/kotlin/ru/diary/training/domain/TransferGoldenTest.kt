package ru.diary.training.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Импорт, очистка и экспорт: результаты совпадают с `parseImport`/`applyImport`/`buildExport` прототипа. */
class TransferGoldenTest {
    private val dirty: String get() = Golden["importDirty"].toString()

    @Test
    fun dirtyImportIsCleanedLikePrototype() {
        val draft = assertNotNull(Transfer.parseImport(dirty))
        Golden.assertJsonEquals(Golden["importDraft"], Golden.draftJson(draft))
        assertEquals(5, draft.skipped, "повреждённые записи пропускаются и считаются")
    }

    @Test
    fun legacyTypesAreMigratedToTrack() {
        val draft = assertNotNull(Transfer.parseImport(dirty))
        val byId = draft.list.associateBy { it.id }
        assertEquals("track" to "handstand", byId.getValue("a2").let { it.type to it.tid })
        assertEquals("track" to "stretch", byId.getValue("a3").let { it.type to it.tid })
        assertEquals("track" to "pullday", byId.getValue("a4").let { it.type to it.tid })
        assertEquals(listOf(5, 5, 6, 999, 0), byId.getValue("a4").sl)
        assertEquals("gym", byId.getValue("a5").type, "неизвестный тип считается залом")
    }

    @Test
    fun replaceImportMatchesPrototype() {
        val draft = assertNotNull(Transfer.parseImport(dirty))
        val result = Transfer.applyImport(Golden.dataFrom(Golden["baseForMerge"].jsonObject), draft, ImportMode.Replace)
        Golden.assertJsonEquals(Golden["afterReplace"], Golden.dataJson(result))
    }

    @Test
    fun mergeImportMatchesPrototype() {
        val draft = assertNotNull(Transfer.parseImport(dirty))
        val result = Transfer.applyImport(Golden.dataFrom(Golden["baseForMerge"].jsonObject), draft, ImportMode.Merge)
        Golden.assertJsonEquals(Golden["afterMerge"], Golden.dataJson(result))
    }

    @Test
    fun exportMatchesPrototype() {
        val data = Golden.dataFrom(Golden["exportInput"].jsonObject)
        val out = Transfer.buildExport(data, "2026-03-18T12:00:00.000Z")
        val expected = Golden["exportOutput"].jsonObject
        // Классические упражнения для зала (gym.json) появились после прототипа: в эталоне их нет,
        // поэтому сравниваем без них, а их наличие в экспорте проверяем отдельно.
        val gymIds = BuiltinData.gymExercises.map { it.id }.toSet()
        val exported = out.getValue("exercises").jsonArray
        assertEquals(gymIds, exported.map { it.jsonObject.getValue("id").jsonPrimitive.content }.filter { it in gymIds }.toSet())
        val protoOnly = JsonObject(out.filterKeys { it != "exportedAt" } + ("exercises" to kotlinx.serialization.json.JsonArray(exported.filter { it.jsonObject.getValue("id").jsonPrimitive.content !in gymIds })))
        Golden.assertJsonEquals(expected, protoOnly)
        assertEquals("dnevnik-trenirovok", out["app"]!!.jsonPrimitive.content)
        assertEquals("3", out["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun exportThenImportLosesNothing() {
        val data = Golden.dataFrom(Golden["exportInput"].jsonObject)
        val text = Transfer.exportText(data, "2026-03-18T12:00:00.000Z")
        val draft = assertNotNull(Transfer.parseImport(text))
        assertEquals(0, draft.skipped)
        val back = Transfer.applyImport(Transfer.initialData(), draft, ImportMode.Replace)
        // порядок сессий в экспорте отсортирован, поэтому сравниваем как множества по id
        assertEquals(data.sessions.sortedBy { it.id }, back.sessions.sortedBy { it.id })
        assertEquals(data.programs, back.programs)
        assertEquals(data.customEx, back.customEx)
        assertEquals(data.plan, back.plan)
        assertEquals(data.maxes, back.maxes)
        assertEquals(data.wlog, back.wlog)
        assertEquals(data.templates, back.templates)
        // и JS-дубль: экспорт, импортированный прототипом, даёт ту же картину
        Golden.assertJsonEquals(Golden["roundTripDraft"], Golden.draftJson(draft), ignore = setOf("ic"))
    }

    @Test
    fun garbageIsRejected() {
        assertNull(Transfer.parseImport("не json"))
        assertNull(Transfer.parseImport("{\"a\":1}"))
        assertNull(Transfer.parseImport("42"))
        assertNull(Transfer.parseImport("null"))
        assertEquals(0, assertNotNull(Transfer.parseImport("[]")).list.size)
        val arr = assertNotNull(Transfer.parseImport("[{\"id\":\"1\",\"date\":\"2026-01-02\",\"type\":\"gym\"}, 5]"))
        assertEquals(1, arr.list.size)
        assertEquals(1, arr.skipped)
        assertNull(arr.plan)
    }

    @Test
    fun templatesMatchPrototype() {
        for (c in Golden["templates"].jsonArray) {
            val id = c.jsonObject.getValue("id").jsonPrimitive.content
            val exp = c.jsonObject.getValue("result").jsonObject
            val got = BuiltinData.builtinTemplate(id)!!
            // У встроенного плана прототип не задаёт `ic`, а `dur` у занятия «подходы за день» равен 0;
            // очистка (`cleanPlan`) подставляет значения по умолчанию, как и при любом импорте в веб-версии.
            Golden.assertJsonEquals(exp.getValue("plan"), Transfer.planJson(got.plan), ignore = setOf("ic", "dur"))
            Golden.assertJsonEquals(exp.getValue("programs"), kotlinx.serialization.json.JsonArray(got.programs.map { Transfer.programJson(it) }))
        }
    }

    @Test
    fun builtinPlansHaveExpectedShape() {
        val vb = BuiltinData.defaultPlan()
        assertEquals(26, Logic.planTotal(vb))
        assertEquals(9, vb.metrics.size)
        assertEquals(listOf("handstand", "stretch", "pullday"), vb.tracks.map { it.id })
        assertEquals(setOf("A", "B", "C"), BuiltinData.originalProgramIds)
        assertEquals(36, BuiltinData.exercises.size)
        assertEquals(listOf("vb", "girl", "blank"), BuiltinData.builtinTemplates.map { it.id })
    }

    @Test
    fun templateSaveApplyDelete() {
        val base = Transfer.initialData().copy(plan = BuiltinData.defaultPlan().copy(start = "2026-02-02"))
        assertTrue(Transfer.saveTemplate(base, "  ") is Transfer.TemplateSave.NoName)
        val saved = (Transfer.saveTemplate(base, "Мой") as Transfer.TemplateSave.Ok).data
        val tpl = saved.templates.single()
        assertEquals("", tpl.plan.start, "дата старта в шаблон не попадает")
        assertEquals(listOf("A", "B", "C"), tpl.programs.map { it.id })

        var full = base
        repeat(Transfer.MAX_TEMPLATES) { full = (Transfer.saveTemplate(full, "t$it") as Transfer.TemplateSave.Ok).data }
        assertTrue(Transfer.saveTemplate(full, "ещё") is Transfer.TemplateSave.LimitReached)

        // применение встроенного шаблона заменяет план и одноимённые программы, сохраняя старт и историю
        val withHistory = base.copy(sessions = Golden.sessions())
        val applied = assertNotNull(Transfer.applyTemplate(withHistory, "girl"))
        assertEquals("Зал 3 раза + волейбол", applied.plan.name)
        assertEquals("2026-02-02", applied.plan.start)
        assertEquals(withHistory.sessions, applied.sessions)
        assertEquals("B · ноги и ягодицы", applied.programs.first { it.id == "B" }.title)
        assertNull(Transfer.applyTemplate(withHistory, "u:нет"))

        val uid = "u:" + tpl.id
        assertEquals("Мой", Transfer.templateName(saved, uid))
        assertTrue(Transfer.deleteTemplate(saved, uid).templates.isEmpty())
    }

    @Test
    fun resetRestoresDefaults() {
        val r = Transfer.reset()
        assertTrue(r.sessions.isEmpty() && r.maxes.isEmpty() && r.wlog.isEmpty() && r.templates.isEmpty() && r.customEx.isEmpty())
        assertEquals(listOf("A", "B", "C"), r.programs.map { it.id })
        assertEquals("", r.plan.start)
    }
}
