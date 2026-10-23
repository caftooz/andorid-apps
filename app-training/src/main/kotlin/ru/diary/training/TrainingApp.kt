package ru.diary.training

import android.app.Application
import android.content.Context
import ru.diary.training.data.SettingsStore
import ru.diary.training.data.TrainingDatabase
import ru.diary.training.data.TrainingRepository

/** Ручной DI: на два приложения с одной БД каждое Hilt даёт больше церемоний, чем пользы. */
class AppContainer(context: Context) {
    val database: TrainingDatabase = TrainingDatabase.create(context)
    val repository = TrainingRepository(database)
    val settings = SettingsStore(context)
}

class TrainingApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.container: AppContainer get() = (applicationContext as TrainingApp).container
