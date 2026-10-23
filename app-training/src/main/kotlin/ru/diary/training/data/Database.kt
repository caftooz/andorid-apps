package ru.diary.training.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Занятие. Поля для запросов вынесены в колонки, вложенное (упражнения, подходы) лежит в `json`
 * в том же формате, что и экспорт, поэтому читается теми же функциями очистки.
 */
@Entity(tableName = "sessions", indices = [Index("date"), Index("type")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val date: String,
    val type: String,
    val start: Long,
    val min: Int,
    val kcal: Int,
    val json: String,
)

@Entity(tableName = "programs")
data class ProgramEntity(@PrimaryKey val id: String, val position: Int, val json: String)

@Entity(tableName = "custom_exercises")
data class CustomExEntity(@PrimaryKey val id: String, val position: Int, val json: String)

/** План хранится одной строкой (`id = 1`): он редактируется целиком и всегда читается целиком. */
@Entity(tableName = "plan")
data class PlanEntity(@PrimaryKey val id: Int = 1, val json: String)

@Entity(tableName = "maxes", indices = [Index("m"), Index("date")])
data class MaxEntity(@PrimaryKey val id: String, val date: String, val m: String, val v: Double)

/** Журнал недели: ключ — понедельник недели. */
@Entity(tableName = "week_log")
data class WeekLogEntity(@PrimaryKey val weekStart: String, val kg: Double, val note: String)

@Entity(tableName = "templates")
data class TemplateEntity(@PrimaryKey val id: String, val position: Int, val json: String)

/** Идущая тренировка. Одна строка (`id = 1`), обновляется после каждого действия. */
@Entity(tableName = "active_workout")
data class ActiveWorkoutEntity(@PrimaryKey val id: Int = 1, val json: String)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM sessions ORDER BY date, start") fun sessions(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions ORDER BY date, start") suspend fun sessionsOnce(): List<SessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(s: SessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSessions(s: List<SessionEntity>)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: String)
    @Query("DELETE FROM sessions") suspend fun clearSessions()

    @Query("SELECT * FROM programs ORDER BY position") fun programs(): Flow<List<ProgramEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPrograms(p: List<ProgramEntity>)
    @Query("DELETE FROM programs") suspend fun clearPrograms()

    @Query("SELECT * FROM custom_exercises ORDER BY position") fun customEx(): Flow<List<CustomExEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCustomEx(p: List<CustomExEntity>)
    @Query("DELETE FROM custom_exercises") suspend fun clearCustomEx()

    @Query("SELECT * FROM plan WHERE id = 1") fun plan(): Flow<PlanEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlan(p: PlanEntity)
    @Query("DELETE FROM plan") suspend fun clearPlan()

    @Query("SELECT * FROM maxes ORDER BY date") fun maxes(): Flow<List<MaxEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMaxes(m: List<MaxEntity>)
    @Query("DELETE FROM maxes WHERE id = :id") suspend fun deleteMax(id: String)
    @Query("DELETE FROM maxes WHERE m = :metric") suspend fun deleteMaxesOf(metric: String)
    @Query("DELETE FROM maxes") suspend fun clearMaxes()

    @Query("SELECT * FROM week_log ORDER BY weekStart") fun weekLog(): Flow<List<WeekLogEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWeek(w: List<WeekLogEntity>)
    @Query("DELETE FROM week_log WHERE weekStart = :ws") suspend fun deleteWeek(ws: String)
    @Query("DELETE FROM week_log") suspend fun clearWeekLog()

    @Query("SELECT * FROM templates ORDER BY position") fun templates(): Flow<List<TemplateEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTemplates(t: List<TemplateEntity>)
    @Query("DELETE FROM templates") suspend fun clearTemplates()

    @Query("SELECT * FROM active_workout WHERE id = 1") fun active(): Flow<ActiveWorkoutEntity?>
    @Query("SELECT * FROM active_workout WHERE id = 1") suspend fun activeOnce(): ActiveWorkoutEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putActive(a: ActiveWorkoutEntity)
    @Query("DELETE FROM active_workout") suspend fun clearActive()

    /** Полная замена данных (импорт «заменить», сброс). Выполняется одной транзакцией. */
    @Transaction
    suspend fun replaceAll(
        sessions: List<SessionEntity>, programs: List<ProgramEntity>, customEx: List<CustomExEntity>, plan: PlanEntity,
        maxes: List<MaxEntity>, weeks: List<WeekLogEntity>, templates: List<TemplateEntity>,
    ) {
        clearSessions(); clearPrograms(); clearCustomEx(); clearMaxes(); clearWeekLog(); clearTemplates()
        upsertSessions(sessions); upsertPrograms(programs); upsertCustomEx(customEx); upsertPlan(plan)
        upsertMaxes(maxes); upsertWeek(weeks); upsertTemplates(templates)
    }
}

@Database(
    entities = [
        SessionEntity::class, ProgramEntity::class, CustomExEntity::class, PlanEntity::class, MaxEntity::class,
        WeekLogEntity::class, TemplateEntity::class, ActiveWorkoutEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun dao(): TrainingDao

    companion object {
        const val NAME = "training.db"

        fun create(context: Context): TrainingDatabase =
            Room.databaseBuilder(context.applicationContext, TrainingDatabase::class.java, NAME).build()
    }
}
