package ru.diary.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Иконки прототипа (контурные, 24×24, линия 2). Пути перенесены из SVG веб-версии; круги записаны дугами.
 * Цвет задаёт `Icon(tint = ...)`.
 */
object DiaryIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private val paths: Map<String, String> = mapOf(
        "gym" to "M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11",
        "vb" to circle(12f, 12f, 9f) + "M12 3c-3 3-3 15 0 18M3.3 9c5 1 13 1 17.4 0M3.3 15c5-1 13-1 17.4 0",
        "home" to "M3 11l9-7 9 7M5 10v10h5v-6h4v6h5V10",
        "rest" to "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z",
        "cardio" to "M3 12h4l2-5 4 10 2-5h6",
        "friends" to circle(9f, 8f, 3f) + circle(17f, 9f, 2.5f) + "M3 20c0-3.5 3-6 6-6s6 2.5 6 6M15 14.5c3 0 6 2 6 5.5",
        "flame" to "M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-5 1-9z",
        "stretch" to circle(12f, 5f, 2f) + "M12 7v6l-4 6M12 13l4 6M6 10h12",
        "star" to "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z",
        "target" to circle(12f, 12f, 9f) + circle(12f, 12f, 5f) + circle(12f, 12f, 1f),
        "check" to "M5 12l5 5 9-10",
        "plus" to "M12 5v14M5 12h14",
        "dish" to "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 3c-2 2-3 5-3 8h3v10",
        "book" to "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM8 7h7M8 11h7",
        "chart" to "M4 20V10M10 20V4M16 20v-8M22 20H2",
        "gear" to circle(12f, 12f, 3f) + "M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2",
    )

    private val cache = HashMap<String, ImageVector>()

    /** Иконка по имени из прототипа; неизвестное имя даёт звезду. */
    fun get(name: String): ImageVector = synchronized(cache) {
        val key = if (name in paths) name else "star"
        cache.getOrPut(key) {
            ImageVector.Builder(name = key, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                .addPath(
                    pathData = addPathNodes(paths.getValue(key)),
                    stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
                .build()
        }
    }

    val names: Set<String> get() = paths.keys
}
