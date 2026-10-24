package ru.diary.nutrition

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import kotlinx.coroutines.runBlocking
import ru.diary.core.contract.DiaryContract
import ru.diary.core.date.Dates
import ru.diary.nutrition.domain.Catalog
import ru.diary.nutrition.domain.Goals
import ru.diary.nutrition.domain.NutritionLogic

/**
 * Отдаёт «Тренировкам» данные по питанию. Только чтение; доступ по signature-permission из манифеста
 * (`readPermission`), плюс повторная проверка в коде на случай неверной конфигурации.
 */
class NutritionProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun enforce() {
        val ctx = context ?: throw SecurityException("нет контекста")
        if (Binder.getCallingUid() == Process.myUid()) return
        if (ctx.checkCallingPermission(DiaryContract.Nutrition.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("нужно разрешение ${DiaryContract.Nutrition.PERMISSION}")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        enforce()
        // Обмен данными временно выключен: сосед получает пустой ответ и просто скрывает свои блоки.
        if (!DiaryContract.SHARING_ENABLED) return MatrixCursor(arrayOf("version"))
        val ctx = context!!
        Catalog.builtin // загружает цели из каталога
        val cursor: MatrixCursor = when (uri.lastPathSegment) {
            DiaryContract.PATH_META -> MatrixCursor(DiaryContract.Nutrition.Meta.ALL).also {
                it.addRow(arrayOf<Any>(DiaryContract.VERSION, Goals.kcal, Goals.proteinMin, Goals.proteinMax))
            }
            DiaryContract.PATH_DAYS -> {
                val from = uri.getQueryParameter(DiaryContract.PARAM_FROM)
                val to = uri.getQueryParameter(DiaryContract.PARAM_TO)
                require(Dates.isDate(from) && Dates.isDate(to)) { "from и to должны быть датами yyyy-MM-dd" }
                val span = Dates.daysBetween(from!!, to!!)
                require(span in 0 until DiaryContract.MAX_RANGE_DAYS) { "диапазон не больше ${DiaryContract.MAX_RANGE_DAYS} дней" }
                val data = runBlocking { (ctx.applicationContext as NutritionApp).container.repository.once() }
                MatrixCursor(DiaryContract.Nutrition.Day.ALL).also { c ->
                    for (i in 0..span) {
                        val d = Dates.addDays(from, i)
                        val t = NutritionLogic.totals(data, d)
                        c.addRow(arrayOf<Any>(d, t.kcal, t.protein, t.fat, t.carbs, t.meals))
                    }
                }
            }
            else -> throw IllegalArgumentException("неизвестный адрес: $uri")
        }
        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String = when (uri.lastPathSegment) {
        DiaryContract.PATH_META -> "vnd.android.cursor.item/vnd.ru.diary.nutrition.meta"
        else -> "vnd.android.cursor.dir/vnd.ru.diary.nutrition.day"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("только чтение")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("только чтение")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("только чтение")
}
