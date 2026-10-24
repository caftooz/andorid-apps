package ru.diary.core.contract

import kotlinx.coroutines.flow.emptyFlow
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.Dispatchers

/** Итог обращения ко второму приложению. */
sealed interface LinkResult<out T> {
    data class Ok<T>(val value: T) : LinkResult<T>

    /** Приложение не установлено. */
    data object NotInstalled : LinkResult<Nothing>

    /** Установлено, но данные недоступны: нет разрешения (другая подпись), провайдер вернул ошибку, иная версия контракта. */
    data class Unavailable(val reason: String) : LinkResult<Nothing>
}

/**
 * Клиент контракта: читает данные соседнего приложения и следит за их изменениями.
 * Любая ошибка превращается в [LinkResult.Unavailable] — интерфейс просто скрывает блок интеграции.
 */
class DiaryClient(private val context: Context) {

    fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) {
        false
    }

    // ---------- Тренировки ----------

    fun trainingMeta(): LinkResult<TrainingMeta> = read(DiaryContract.Training.PACKAGE, DiaryContract.Training.META_URI) { c ->
        if (!c.moveToFirst()) return@read null
        TrainingMeta(
            version = c.int(DiaryContract.Training.Meta.VERSION) ?: 0,
            lastWeightKg = c.double(DiaryContract.Training.Meta.LAST_WEIGHT_KG),
            proteinEnabled = (c.int(DiaryContract.Training.Meta.PROTEIN_ENABLED) ?: 0) == 1,
        )
    }

    fun trainingDays(from: String, to: String): LinkResult<List<TrainingDay>> =
        read(DiaryContract.Training.PACKAGE, DiaryContract.daysUri(DiaryContract.Training.DAYS_URI, from, to)) { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        TrainingDay(
                            date = c.string(DiaryContract.Training.Day.DATE) ?: continue,
                            kcalBurned = c.int(DiaryContract.Training.Day.KCAL_BURNED) ?: 0,
                            minutes = c.int(DiaryContract.Training.Day.MINUTES) ?: 0,
                            dayType = c.string(DiaryContract.Training.Day.DAY_TYPE) ?: DiaryContract.DAY_REST,
                            workoutTitle = c.string(DiaryContract.Training.Day.WORKOUT_TITLE).orEmpty(),
                        ),
                    )
                }
            }
        }

    // ---------- Рацион ----------

    fun nutritionMeta(): LinkResult<NutritionMeta> = read(DiaryContract.Nutrition.PACKAGE, DiaryContract.Nutrition.META_URI) { c ->
        if (!c.moveToFirst()) return@read null
        NutritionMeta(
            version = c.int(DiaryContract.Nutrition.Meta.VERSION) ?: 0,
            goalKcal = c.int(DiaryContract.Nutrition.Meta.GOAL_KCAL) ?: 0,
            goalProteinMin = c.int(DiaryContract.Nutrition.Meta.GOAL_PROTEIN_MIN) ?: 0,
            goalProteinMax = c.int(DiaryContract.Nutrition.Meta.GOAL_PROTEIN_MAX) ?: 0,
        )
    }

    fun nutritionDays(from: String, to: String): LinkResult<List<NutritionDay>> =
        read(DiaryContract.Nutrition.PACKAGE, DiaryContract.daysUri(DiaryContract.Nutrition.DAYS_URI, from, to)) { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        NutritionDay(
                            date = c.string(DiaryContract.Nutrition.Day.DATE) ?: continue,
                            kcalEaten = c.int(DiaryContract.Nutrition.Day.KCAL_EATEN) ?: 0,
                            proteinG = c.int(DiaryContract.Nutrition.Day.PROTEIN_G) ?: 0,
                            fatG = c.int(DiaryContract.Nutrition.Day.FAT_G) ?: 0,
                            carbsG = c.int(DiaryContract.Nutrition.Day.CARBS_G) ?: 0,
                            meals = c.int(DiaryContract.Nutrition.Day.MEALS) ?: 0,
                        ),
                    )
                }
            }
        }

    // ---------- наблюдение ----------

    /**
     * Поток сигналов «данные соседа изменились». Эмитит сразу при подписке, затем при каждом `notifyChange`.
     * Если приложение установили или удалили во время работы, наблюдатель остаётся жить: `notifyChange` придёт позже.
     */
    fun changes(authority: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        val registered = runCatching { context.contentResolver.registerContentObserver(authority, true, observer) }.isSuccess
        trySend(Unit)
        awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }.conflate().flowOn(Dispatchers.Main.immediate)

    /** Установка, обновление и удаление соседнего приложения во время работы. */
    fun packageEvents(pkg: String): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.data?.schemeSpecificPart == pkg) trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val ok = runCatching { context.registerReceiver(receiver, filter) }.isSuccess
        awaitClose { if (ok) runCatching { context.unregisterReceiver(receiver) } }
    }.conflate()

    /** Единый сигнал «пора перечитать данные соседа»: изменения данных плюс установка и удаление приложения. */
    fun watch(pkg: String, authorityUri: Uri): Flow<Unit> =
        if (!DiaryContract.SHARING_ENABLED) emptyFlow() else merge(changes(authorityUri), packageEvents(pkg)).conflate()

    private fun <T> read(pkg: String, uri: Uri, parse: (Cursor) -> T?): LinkResult<T> {
        if (!DiaryContract.SHARING_ENABLED) return LinkResult.Unavailable("обмен данными выключен")
        if (!isInstalled(pkg)) return LinkResult.NotInstalled
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
                ?: return LinkResult.Unavailable("провайдер не ответил")
            cursor.use { c ->
                val v = parse(c) ?: return LinkResult.Unavailable("пустой ответ")
                LinkResult.Ok(v)
            }
        } catch (e: SecurityException) {
            LinkResult.Unavailable("нет разрешения: другая подпись приложения")
        } catch (e: Exception) {
            LinkResult.Unavailable(e.javaClass.simpleName)
        }
    }

    private fun Cursor.idx(name: String): Int = getColumnIndex(name)
    private fun Cursor.string(name: String): String? = idx(name).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }
    private fun Cursor.int(name: String): Int? = idx(name).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }
    private fun Cursor.double(name: String): Double? = idx(name).takeIf { it >= 0 && !isNull(it) }?.let { getDouble(it) }
}
