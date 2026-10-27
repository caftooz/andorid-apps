package ru.diary.training.domain

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PictosTest {
    @Test fun everyBuiltinExerciseHasPictoInsideFrame() {
        for (d in BuiltinData.allExercises) {
            val p = assertNotNull(Pictos.forEx(d.id), "пиктограмма для ${d.id}")
            assertTrue(p.poses.size in 1..2, d.id)
            val frames = p.poses.map { Pictos.frame(it) }
            for (f in frames) for ((name, pt) in f.j) {
                assertTrue(pt.x.isFinite() && pt.y.isFinite(), "${d.id}.$name")
                assertTrue(pt.x in -12f..112f && pt.y in -12f..104f, "${d.id}.$name вне кадра: $pt")
            }
            if (frames.size == 2) p.arrows.forEach { assertTrue(it in frames[0].j && it in frames[1].j, "${d.id}: стрелка $it") }
        }
    }

    @Test fun anchoredFootStaysOnFloor() {
        val f = Pictos.frame(kotlinx.serialization.json.buildJsonObject {
            put("A", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(50), kotlinx.serialization.json.JsonPrimitive(93))))
        })
        val a = f.j.getValue("ankle")
        assertTrue(kotlin.math.abs(a.x - 50f) < 0.01f && kotlin.math.abs(a.y - 93f) < 0.01f)
        assertTrue(kotlin.math.abs(f.j.getValue("hip").y - 55f) < 0.01f, "стоя таз на 38 выше стопы")
    }
}
