package ru.diary.nutrition

import android.app.Application
import android.content.Context
import ru.diary.core.contract.DiaryClient
import ru.diary.core.contract.DiaryContract
import ru.diary.nutrition.data.NutritionDatabase
import ru.diary.nutrition.data.NutritionRepository
import ru.diary.nutrition.data.SettingsStore

/** Ручной DI. После каждой записи сообщаем второму приложению, что данные изменились. */
class AppContainer(private val context: Context) {
    val database: NutritionDatabase = NutritionDatabase.create(context)
    val repository = NutritionRepository(database) {
        val r = context.contentResolver
        runCatching {
            r.notifyChange(DiaryContract.Nutrition.DAYS_URI, null)
            r.notifyChange(DiaryContract.Nutrition.META_URI, null)
        }
    }
    val settings = SettingsStore(context)
    val client = DiaryClient(context)
}

class NutritionApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.container: AppContainer get() = (applicationContext as NutritionApp).container
