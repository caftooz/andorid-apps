package ru.diary.nutrition.data

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

/** Приём пищи. */
@Entity(tableName = "entries", indices = [Index("date")])
data class EntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val time: String,
    val dish: String?,
    val name: String,
    val portion: Double,
    val kcal: Int,
    val protein: Int,
    val plan: Boolean,
)

/** Своё блюдо пользователя. */
@Entity(tableName = "custom_dishes")
data class CustomDishEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val name: String,
    val cat: String,
    val kcal: Int,
    val p: Int,
    val notes: String,
)

@Dao
interface NutritionDao {
    @Query("SELECT * FROM entries ORDER BY date, time") fun entries(): Flow<List<EntryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEntries(e: List<EntryEntity>)
    @Query("DELETE FROM entries WHERE id = :id") suspend fun deleteEntry(id: String)
    @Query("DELETE FROM entries") suspend fun clearEntries()

    @Query("SELECT * FROM custom_dishes ORDER BY position") fun custom(): Flow<List<CustomDishEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCustom(c: List<CustomDishEntity>)
    @Query("DELETE FROM custom_dishes") suspend fun clearCustom()

    @Transaction
    suspend fun replaceAll(e: List<EntryEntity>, c: List<CustomDishEntity>) {
        clearEntries(); clearCustom(); upsertEntries(e); upsertCustom(c)
    }
}

@Database(entities = [EntryEntity::class, CustomDishEntity::class], version = 1, exportSchema = true)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun dao(): NutritionDao

    companion object {
        const val NAME = "nutrition.db"
        fun create(context: Context): NutritionDatabase = Room.databaseBuilder(context.applicationContext, NutritionDatabase::class.java, NAME).build()
    }
}
