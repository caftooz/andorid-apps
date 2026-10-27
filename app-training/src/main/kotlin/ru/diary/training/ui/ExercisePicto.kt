package ru.diary.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.diary.training.domain.Pictos
import ru.diary.training.domain.Pictos.Pt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Цвета табличек на тренажёрах: тёмная плашка, светлая фигура, серые снаряды, жёлтая стрелка.
// Не зависят от темы приложения — пиктограмма везде выглядит одинаково.
private val PlateBg = Color(0xFF202126)
private val Fig = Color(0xFFECECF0)
private val FarLimb = Color(0xFF9698A0)
private val Equip = Color(0xFF6E707A)
private val Arrow = Color(0xFFF5B428)

private const val TOP = 10f      // запас сверху в единицах кадра: руки над головой, прыжки
private const val FRAME_H = 108f // высота кадра с запасом (пол на y = 96)

/** Пиктограмма «как делать»: два положения 1 → 2 (или одно для статики) и стрелка движения. */
@Composable
fun ExercisePicto(picto: Pictos.Picto, modifier: Modifier = Modifier, description: String = "") {
    val frames = remember(picto.id) { picto.poses.map { Pictos.frame(it) } }
    val two = frames.size > 1
    Box(
        modifier.fillMaxWidth().background(PlateBg, RoundedCornerShape(18.dp)).semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(200f / FRAME_H).padding(horizontal = 4.dp)) {
            val k = size.width / 200f
            if (two) {
                figure(frames[0], picto.eq, 0f, k)
                figure(frames[1], picto.eq2, 100f, k)
                for (j in picto.arrows) {
                    val a = frames[0].j[j]; val b = frames[1].j[j]
                    if (a != null && b != null) arrow(a, b, k)
                }
            } else figure(frames[0], picto.eq, 50f, k)
        }
        if (two) {
            Text("1", color = FarLimb, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 4.dp))
            Text("2", color = FarLimb, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp, start = 12.dp))
        }
    }
}

private fun DrawScope.o(p: Pt, ox: Float, k: Float) = Offset((p.x + ox) * k, (p.y + TOP) * k)

private fun DrawScope.seg(a: Pt, b: Pt, w: Float, c: Color, ox: Float, k: Float) =
    drawLine(c, o(a, ox, k), o(b, ox, k), strokeWidth = w * k, cap = StrokeCap.Round)

private fun DrawScope.dot(c0: Pt, r: Float, c: Color, ox: Float, k: Float, ring: Boolean = false) {
    drawCircle(c, r * k, o(c0, ox, k))
    if (ring) drawCircle(PlateBg, r * k, o(c0, ox, k), style = Stroke(1.5f * k))
}

private fun Pictos.Frame.p(ref: String?): Pt? = ref?.let { j[it] }

private fun DrawScope.figure(f: Pictos.Frame, items: List<Pictos.Item>, ox: Float, k: Float) {
    seg(Pt(2f, 96f), Pt(98f, 96f), 1.5f, Equip, ox, k)
    equip(f, items, ox, k, top = false)
    for ((a, b) in f.far) seg(f.j.getValue(a), f.j.getValue(b), 6f, FarLimb, ox, k)
    for ((a, b) in f.torso) {
        val w = if (a == "hip" && b == "sh") 9f else 6f
        seg(f.j.getValue(a), f.j.getValue(b), w + 2.4f, PlateBg, ox, k); seg(f.j.getValue(a), f.j.getValue(b), w, Fig, ox, k)
    }
    dot(f.j.getValue("head"), 6.7f, PlateBg, ox, k); dot(f.j.getValue("head"), 5.5f, Fig, ox, k)
    // Обводка цветом плашки: рука поверх корпуса не сливается с ним. Сначала обводка всей группы, потом заливка — без швов в суставах.
    for (grp in listOf(f.legs, f.arms)) {
        for ((a, b) in grp) seg(f.j.getValue(a), f.j.getValue(b), 8.4f, PlateBg, ox, k)
        for ((a, b) in grp) seg(f.j.getValue(a), f.j.getValue(b), 6f, Fig, ox, k)
    }
    equip(f, items, ox, k, top = true)
}

private val ON_TOP = setOf("plate", "db", "ball", "pad", "handle")

private fun DrawScope.equip(f: Pictos.Frame, items: List<Pictos.Item>, ox: Float, k: Float, top: Boolean) {
    for (it in items) {
        if ((it.kind in ON_TOP) != top) continue
        val p = it.p
        when (it.kind) {
            "bench" -> {
                seg(Pt(p[0], p[2]), Pt(p[1], p[2]), 4f, Equip, ox, k)
                seg(Pt(p[0] + 4, p[2]), Pt(p[0] + 4, 96f), 2.5f, Equip, ox, k); seg(Pt(p[1] - 4, p[2]), Pt(p[1] - 4, 96f), 2.5f, Equip, ox, k)
            }
            "line" -> seg(Pt(p[0], p[1]), Pt(p[2], p[3]), it.w ?: 3f, Equip, ox, k)
            "box" -> drawRoundRect(
                Equip, o(Pt(p[0], p[1]), ox, k), androidx.compose.ui.geometry.Size(p[2] * k, p[3] * k),
                androidx.compose.ui.geometry.CornerRadius(2f * k),
            )
            "circ" -> dot(Pt(p[0], p[1]), p[2], Equip, ox, k)
            "cable" -> f.p(it.to)?.let { h -> seg(Pt(p[0], p[1]), h, 1.4f, Equip, ox, k); dot(Pt(p[0], p[1]), 3f, Equip, ox, k) }
            "loop" -> f.p(it.at)?.let { a ->
                val b = it.b ?: return@let
                for (c in listOfNotNull(it.c1, it.c2)) {
                    val path = Path().apply { moveTo(o(a, ox, k).x, o(a, ox, k).y); quadraticTo(o(c, ox, k).x, o(c, ox, k).y, o(b, ox, k).x, o(b, ox, k).y) }
                    drawPath(path, Equip, style = Stroke(1.6f * k))
                }
            }
            "postj" -> f.p(it.at)?.let { a -> val s = a + it.d; seg(s, Pt(s.x, 96f), 2.6f, Equip, ox, k) }
            "plate" -> f.p(it.at)?.let { a -> dot(a + it.d, it.r ?: 7f, Equip, ox, k, ring = true) }
            "db" -> f.p(it.at)?.let { a -> dot(a + it.d, it.r ?: 3.6f, Equip, ox, k, ring = true) }
            "ball" -> f.p(it.at)?.let { a -> dot(a + it.d, it.r ?: 5.5f, Equip, ox, k, ring = true) }
            "pad" -> f.p(it.at)?.let { a -> dot(a + it.d, it.r ?: 3.4f, Equip, ox, k, ring = true) }
            "handle" -> f.p(it.at)?.let { a ->
                val c = a + it.d
                val r = Math.toRadians(it.a.toDouble())
                val dv = Pt((cos(r) * it.l / 2).toFloat(), (sin(r) * it.l / 2).toFloat())
                seg(c - dv, c + dv, 2.6f, Equip, ox, k)
            }
        }
    }
}

/** Жёлтая стрелка в первом кадре: от положения сустава в кадре 1 к положению в кадре 2, со сдвигом вбок. */
private fun DrawScope.arrow(a: Pt, b: Pt, k: Float, off: Float = 7f) {
    val vx = b.x - a.x; val vy = b.y - a.y
    val len = hypot(vx, vy)
    if (len < 3f) return
    val n = Pt(-vy / len * off, vx / len * off)
    val s = Pt(a.x + vx * 0.2f, a.y + vy * 0.2f) + n
    val e = Pt(a.x + vx * 0.8f, a.y + vy * 0.8f) + n
    seg(s, e, 2.2f, Arrow, 0f, k)
    val ux = vx / len; val uy = vy / len
    val h1 = Pt(e.x - ux * 4 - uy * 3, e.y - uy * 4 + ux * 3)
    val h2 = Pt(e.x - ux * 4 + uy * 3, e.y - uy * 4 - ux * 3)
    drawPath(Path().apply { moveTo(o(e, 0f, k).x, o(e, 0f, k).y); lineTo(o(h1, 0f, k).x, o(h1, 0f, k).y); lineTo(o(h2, 0f, k).x, o(h2, 0f, k).y); close() }, Arrow)
}
