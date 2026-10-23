package ru.diary.training.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first
import ru.diary.core.date.Dates
import ru.diary.training.MainActivity
import ru.diary.training.container
import ru.diary.training.domain.Logic
import ru.diary.training.ui.Texts

/** Виджет «Сегодня по плану»: тип дня, программа, неделя плана и выполнено ли занятие. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = context.container.repository.data.first()
        val today = Dates.today()
        val day = Logic.planDay(data.plan, today)
        val week = Logic.planWeek(data.plan, today)
        val title = Texts.dayLabel(day, data.programs)
        val sub = when {
            week != null && Logic.inPlan(data.plan, week) -> "Неделя $week из ${Logic.planTotal(data.plan)} · " + Logic.blockOf(data.plan, week).name
            else -> data.plan.name
        }
        val done = data.sessions.count { it.date == today }
        provideContent {
            GlanceTheme {
                Content(title, sub, if (done > 0) "Сегодня записано занятий: $done" else "Пока без записей", context)
            }
        }
    }

    @Composable
    private fun Content(title: String, sub: String, status: String, context: Context) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) {
            Text("Сегодня", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            Text(sub, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            Text(status, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp), modifier = GlanceModifier.padding(top = 6.dp))
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

