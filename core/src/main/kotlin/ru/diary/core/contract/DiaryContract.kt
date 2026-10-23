package ru.diary.core.contract

import android.net.Uri

/**
 * Контракт обмена данными между «Тренировками» и «Рационом».
 *
 * Каждое приложение отдаёт второму данные через `ContentProvider` только для чтения. Доступ защищён
 * custom-permission с `protectionLevel="signature"`: читать могут лишь приложения, подписанные тем же ключом.
 *
 * Схема адресов (одинакова для обоих провайдеров):
 *  - `content://<authority>/meta`  — одна строка со справочными значениями;
 *  - `content://<authority>/days?from=yyyy-MM-dd&to=yyyy-MM-dd` — по строке на каждую дату диапазона (включительно).
 *
 * Версия контракта лежит в колонке `version` строки `meta`. Новые колонки добавляются только в конец;
 * клиент обязан игнорировать неизвестные колонки и работать при отсутствии известных.
 */
object DiaryContract {
    const val VERSION = 1

    /** Максимальная длина диапазона дат в одном запросе. */
    const val MAX_RANGE_DAYS = 400

    const val PATH_META = "meta"
    const val PATH_DAYS = "days"
    const val PARAM_FROM = "from"
    const val PARAM_TO = "to"

    /** Типы дня плана тренировок. */
    const val DAY_GYM = "gym"
    const val DAY_HOME = "home"
    const val DAY_VOLLEY = "vb"
    const val DAY_REST = "rest"

    object Training {
        const val PACKAGE = "ru.diary.training"
        const val AUTHORITY = "ru.diary.training.provider"
        const val PERMISSION = "ru.diary.training.permission.READ_DATA"

        val META_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_META")
        val DAYS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_DAYS")

        object Meta {
            const val VERSION = "version"
            /** Последний вес из журнала недели, кг; `NULL`, если веса нет. */
            const val LAST_WEIGHT_KG = "last_weight_kg"
            /** 1, если в плане включён показ нормы белка. */
            const val PROTEIN_ENABLED = "protein_enabled"
            val ALL = arrayOf(VERSION, LAST_WEIGHT_KG, PROTEIN_ENABLED)
        }

        object Day {
            const val DATE = "date"
            /** Оценка пользователя: ккал на занятиях за день (зал, кардио, волейбол, другое). */
            const val KCAL_BURNED = "kcal_burned"
            const val MINUTES = "minutes"
            /** `gym`, `home`, `vb` или `rest` по плану на этот день недели. */
            const val DAY_TYPE = "day_type"
            /** Название тренировки: заголовок программы из плана или записанного занятия, иначе пусто. */
            const val WORKOUT_TITLE = "workout_title"
            val ALL = arrayOf(DATE, KCAL_BURNED, MINUTES, DAY_TYPE, WORKOUT_TITLE)
        }
    }

    object Nutrition {
        const val PACKAGE = "ru.diary.nutrition"
        const val AUTHORITY = "ru.diary.nutrition.provider"
        const val PERMISSION = "ru.diary.nutrition.permission.READ_DATA"

        val META_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_META")
        val DAYS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_DAYS")

        object Meta {
            const val VERSION = "version"
            const val GOAL_KCAL = "goal_kcal"
            const val GOAL_PROTEIN_MIN = "goal_protein_min"
            const val GOAL_PROTEIN_MAX = "goal_protein_max"
            val ALL = arrayOf(VERSION, GOAL_KCAL, GOAL_PROTEIN_MIN, GOAL_PROTEIN_MAX)
        }

        object Day {
            const val DATE = "date"
            const val KCAL_EATEN = "kcal_eaten"
            const val PROTEIN_G = "protein_g"
            const val FAT_G = "fat_g"
            const val CARBS_G = "carbs_g"
            const val MEALS = "meals"
            val ALL = arrayOf(DATE, KCAL_EATEN, PROTEIN_G, FAT_G, CARBS_G, MEALS)
        }
    }

    fun daysUri(base: Uri, from: String, to: String): Uri =
        base.buildUpon().appendQueryParameter(PARAM_FROM, from).appendQueryParameter(PARAM_TO, to).build()
}

/** День из «Тренировок». */
data class TrainingDay(val date: String, val kcalBurned: Int, val minutes: Int, val dayType: String, val workoutTitle: String)

data class TrainingMeta(val version: Int, val lastWeightKg: Double?, val proteinEnabled: Boolean)

/** День из «Рациона». */
data class NutritionDay(val date: String, val kcalEaten: Int, val proteinG: Int, val fatG: Int, val carbsG: Int, val meals: Int)

data class NutritionMeta(val version: Int, val goalKcal: Int, val goalProteinMin: Int, val goalProteinMax: Int)
