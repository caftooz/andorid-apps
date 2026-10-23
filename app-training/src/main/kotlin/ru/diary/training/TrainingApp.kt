package ru.diary.training

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.diary.core.contract.DiaryClient
import ru.diary.core.update.Updater
import ru.diary.core.contract.DiaryContract
import ru.diary.training.data.SettingsStore
import ru.diary.training.data.TrainingDatabase
import ru.diary.training.data.TrainingRepository
import ru.diary.training.widget.TodayWidget

/** Ручной DI: на два приложения с одной БД каждое Hilt даёт больше церемоний, чем пользы. */
class AppContainer(context: Context) {
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: TrainingDatabase = TrainingDatabase.create(context)
    val repository = TrainingRepository(database) {
        // Сообщаем «Рациону», что данные изменились: он обновит баланс без перезапуска.
        val r = context.contentResolver
        runCatching {
            r.notifyChange(DiaryContract.Training.DAYS_URI, null)
            r.notifyChange(DiaryContract.Training.META_URI, null)
        }
        widgetScope.launch { runCatching { TodayWidget().updateAll(context) } }
    }
    val settings = SettingsStore(context)
    val client = DiaryClient(context)
    val updater = Updater(context, "caftooz", "andorid-apps", "app-training-release.apk")
}

class TrainingApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.container: AppContainer get() = (applicationContext as TrainingApp).container
