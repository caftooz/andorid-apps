package ru.diary.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import ru.diary.core.ui.DiaryAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.diary.core.charts.MeterBar
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.ui.DiaryCard
import ru.diary.core.ui.Muted
import ru.diary.training.domain.Session

/**
 * Интеграция с «Рационом»: плитка «Питание» на «Сегодня», итог после тренировки,
 * средние ккал по неделям для графика веса. Всё скрыто, пока «Рацион» не установлен или не отвечает.
 */
class LiveIntegration(
    private val link: NutritionLink,
    private val finished: Session?,
    private val dismiss: () -> Unit,
) : TrainingIntegration {

    @Composable
    override fun TodayTile() {
        val l = link as? NutritionLink.Ready ?: return
        val d = l.days[Dates.today()]
        val kcal = d?.kcalEaten ?: 0
        val protein = d?.proteinG ?: 0
        val goalK = l.meta.goalKcal.takeIf { it > 0 }
        val goalP = l.meta.goalProteinMin.takeIf { it > 0 }
        DiaryCard {
            Text("Питание", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Съедено: $kcal" + (goalK?.let { " из $it" } ?: "") + " ккал", style = MaterialTheme.typography.bodyMedium)
                    if (goalK != null) MeterBar((kcal.toFloat() / goalK).coerceAtMost(1f), "Съедено $kcal из $goalK ккал")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Белок: $protein" + (goalP?.let { " из $it" } ?: "") + " г", style = MaterialTheme.typography.bodyMedium)
                    if (goalP != null) MeterBar((protein.toFloat() / goalP).coerceAtMost(1f), "Белок $protein из $goalP граммов", color = DiaryTheme.extras.protein)
                }
            }
        }
    }

    @Composable
    override fun FinishSummary(session: Session, onDismiss: () -> Unit) {
        val l = link as? NutritionLink.Ready
        DiaryAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Тренировка сохранена") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${session.min} мин" + (session.kcal?.let { " · потрачено на тренировке: $it ккал" } ?: ""))
                    if (l != null) {
                        val eaten = l.days[session.date]?.kcalEaten ?: 0
                        Text(
                            "Съедено за ${if (session.date == Dates.today()) "сегодня" else session.date}: $eaten ккал",
                            modifier = Modifier.semantics { contentDescription = "Съедено за день $eaten ккал" },
                        )
                        Muted("«Потрачено» — ваша оценка расхода на занятии, а не общий расход. Это не медицинский вывод.")
                    }
                }
            },
            confirmButton = { TextButton(onDismiss) { Text("Закрыть") } },
        )
    }

    @Composable
    override fun weeklyKcal(weekStarts: List<String>): Map<String, Int>? {
        val l = link as? NutritionLink.Ready ?: return null
        val out = LinkedHashMap<String, Int>()
        for (ws in weekStarts) {
            val days = (0 until 7).mapNotNull { l.days[Dates.addDays(ws, it)] }.filter { it.meals > 0 }
            if (days.isNotEmpty()) out[ws] = days.sumOf { it.kcalEaten } / days.size
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** Показывает итог после тренировки, пока он не закрыт. */
    @Composable
    fun Overlay() {
        if (finished != null) FinishSummary(finished, dismiss)
    }
}
