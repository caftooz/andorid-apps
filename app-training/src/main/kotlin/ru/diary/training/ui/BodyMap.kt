package ru.diary.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.diary.core.ui.Muted
import ru.diary.training.domain.Muscles

/** Цвет основных мышц на схеме; вспомогательные — он же, но прозрачнее. */
val MuscleHot = Color(0xFFE8572A)

/** Одна фигура (спереди или сзади) с подсвеченными мышцами. */
@Composable
fun BodyFigure(shapes: List<Muscles.Shape>, load: Muscles.Load, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val paths = remember(shapes) {
        shapes.map { s -> s.id to s.polygons.map { p -> Path().apply { moveTo(p[0], p[1]); for (k in 2 until p.size step 2) lineTo(p[k], p[k + 1]); close() } } }
    }
    Canvas(modifier.aspectRatio(0.5f)) {
        val k = minOf(size.width / 100f, size.height / 200f)
        val dx = (size.width - 100f * k) / 2f
        drawContext.transform.translate(dx, 0f)
        drawContext.transform.scale(k, k, androidx.compose.ui.geometry.Offset.Zero)
        for ((id, list) in paths) {
            val c = when (id) {
                in load.primary -> MuscleHot
                in load.secondary -> MuscleHot.copy(alpha = 0.42f)
                "head", "knees" -> faint
                else -> base
            }
            // Тонкая обводка тем же цветом скругляет острые углы многоугольников.
            for (p in list) { drawPath(p, c, style = Fill); drawPath(p, c, style = Stroke(width = 0.8f, join = StrokeJoin.Round)) }
        }
    }
}

/** Схема «спереди и сзади» с подписью основных и вспомогательных мышц. */
@Composable
fun BodyMap(load: Muscles.Load, modifier: Modifier = Modifier) {
    val desc = "Основные мышцы: " + load.primary.joinToString(", ") { Muscles.name(it).lowercase() } +
        if (load.secondary.isNotEmpty()) ". Вспомогательные: " + load.secondary.joinToString(", ") { Muscles.name(it).lowercase() } else ""
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BodyFigure(Muscles.front, load, Modifier.size(width = 100.dp, height = 200.dp))
                Muted("спереди")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BodyFigure(Muscles.back, load, Modifier.size(width = 100.dp, height = 200.dp))
                Muted("сзади")
            }
        }
        Legend(MuscleHot, "Основные", load.primary)
        if (load.secondary.isNotEmpty()) Legend(MuscleHot.copy(alpha = 0.42f), "Вспомогательные", load.secondary)
    }
}

@Composable
private fun Legend(color: Color, title: String, ids: List<String>) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 4.dp).size(12.dp).background(color, CircleShape))
        Text(
            "$title: " + ids.joinToString(", ") { Muscles.name(it).lowercase() },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Маленькая фигура для списков: та сторона, где основная нагрузка. */
@Composable
fun BodyThumb(load: Muscles.Load, modifier: Modifier = Modifier) {
    BodyFigure(if (Muscles.mainSideIsFront(load)) Muscles.front else Muscles.back, load, modifier.size(width = 36.dp, height = 72.dp))
}
