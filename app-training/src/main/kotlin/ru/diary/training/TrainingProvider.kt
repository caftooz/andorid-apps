package ru.diary.training

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.diary.core.contract.DiaryContract
import ru.diary.core.date.Dates
import ru.diary.training.domain.Logic
import ru.diary.training.domain.TrainingData
import ru.diary.training.ui.Texts

/**
 * Отдаёт «Рациону» данные по тренировкам: ккал, минуты, тип дня по плану, название тренировки, последний вес.
 * Только чтение. Доступ по signature-permission (`readPermission` в манифесте) плюс повторная проверка здесь.
 */
class TrainingProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun enforce() {
        val ctx = context ?: throw SecurityException("нет контекста")
        if (Binder.getCallingUid() == Process.myUid()) return
        if (ctx.checkCallingPermission(DiaryContract.Training.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("нужно разрешение ${DiaryContract.Training.PERMISSION}")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        enforce()
        // Обмен данными временно выключен: сосед получает пустой ответ и просто скрывает свои блоки.
        if (!DiaryContract.SHARING_ENABLED) return MatrixCursor(arrayOf("version"))
        val ctx = context!!
        val cursor: MatrixCursor = when (uri.lastPathSegment) {
            DiaryContract.PATH_META -> {
                val d = load(ctx)
                val kg = d.wlog.filter { it.value.kg > 0 }.keys.maxOrNull()?.let { d.wlog.getValue(it).kg }
                MatrixCursor(DiaryContract.Training.Meta.ALL).also {
                    it.addRow(arrayOf<Any?>(DiaryContract.VERSION, kg, if (d.plan.protein) 1 else 0))
                }
            }
            DiaryContract.PATH_DAYS -> {
                val from = uri.getQueryParameter(DiaryContract.PARAM_FROM)
                val to = uri.getQueryParameter(DiaryContract.PARAM_TO)
                require(Dates.isDate(from) && Dates.isDate(to)) { "from и to должны быть датами yyyy-MM-dd" }
                val span = Dates.daysBetween(from!!, to!!)
                require(span in 0 until DiaryContract.MAX_RANGE_DAYS) { "диапазон не больше ${DiaryContract.MAX_RANGE_DAYS} дней" }
                val d = load(ctx)
                MatrixCursor(DiaryContract.Training.Day.ALL).also { c ->
                    for (i in 0..span) c.addRow(dayRow(d, Dates.addDays(from, i)))
                }
            }
            else -> throw IllegalArgumentException("неизвестный адрес: $uri")
        }
        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    private fun load(ctx: android.content.Context): TrainingData =
        runBlocking { (ctx.applicationContext as TrainingApp).container.repository.data.first() }

    private fun dayRow(d: TrainingData, date: String): Array<Any?> {
        val sessions = d.sessions.filter { it.date == date }
        val plan = d.plan.days[Dates.dayIndex(date)]
        val title = sessions.firstOrNull { it.type == "gym" }?.let { Texts.sessionTitle(it) }
            ?: sessions.firstOrNull { it.type != "track" }?.let { Texts.typeName(it, d.plan) }
            ?: if (plan.k == "gym" && plan.prog.isNotEmpty()) d.programs.firstOrNull { it.id == plan.prog }?.title.orEmpty() else ""
        return arrayOf(date, sessions.sumOf { it.kcal ?: 0 }, sessions.sumOf { it.min }, plan.k, title)
    }

    override fun getType(uri: Uri): String = when (uri.lastPathSegment) {
        DiaryContract.PATH_META -> "vnd.android.cursor.item/vnd.ru.diary.training.meta"
        else -> "vnd.android.cursor.dir/vnd.ru.diary.training.day"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("только чтение")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("только чтение")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("только чтение")

    @Suppress("unused")
    private fun keep() = Logic
}
