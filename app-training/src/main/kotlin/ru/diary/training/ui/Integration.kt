package ru.diary.training.ui

import androidx.compose.runtime.Composable
import ru.diary.training.domain.Session

/**
 * Точки, где «Тренировки» показывают данные «Рациона», если второе приложение установлено.
 * Без него все блоки пустые, и интерфейс работает как обычно.
 */
interface TrainingIntegration {
    /** Плитка «Питание» на вкладке «Сегодня». */
    @Composable fun TodayTile()

    /** Итог после завершения тренировки: потрачено и съедено сегодня. */
    @Composable fun FinishSummary(session: Session, onDismiss: () -> Unit)

    /** Средние ккал за недели (понедельник → ккал) для графика рядом с весом, либо `null`, если данных нет. */
    @Composable fun weeklyKcal(weekStarts: List<String>): Map<String, Int>?
}

object NoIntegration : TrainingIntegration {
    @Composable override fun TodayTile() = Unit
    @Composable override fun FinishSummary(session: Session, onDismiss: () -> Unit) = Unit
    @Composable override fun weeklyKcal(weekStarts: List<String>): Map<String, Int>? = null
}
