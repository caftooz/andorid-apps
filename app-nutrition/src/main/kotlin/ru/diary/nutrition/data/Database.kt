package ru.diary.nutrition.data

import androidx.room.ColumnInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
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
    val fat: Int? = null,
    val carbs: Int? = null,
    val fiber: Double? = null,
)

/** Продукт по штрих-коду, КБЖУ на 100 г. */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val code: String,
    val name: String,
    val brand: String,
    val kcal: Double,
    val p: Double,
    val f: Double,
    val c: Double,
    val packG: Int?,
    val source: String,
    val updated: Long,
    @ColumnInfo(defaultValue = "0") val fiber: Double = 0.0,
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

    @Query("SELECT * FROM products ORDER BY updated DESC") fun products(): Flow<List<ProductEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProducts(p: List<ProductEntity>)
    @Query("DELETE FROM products WHERE code = :code") suspend fun deleteProduct(code: String)
    @Query("DELETE FROM products") suspend fun clearProducts()

    @Transaction
    suspend fun replaceAll(e: List<EntryEntity>, c: List<CustomDishEntity>, p: List<ProductEntity>) {
        clearEntries(); clearCustom(); clearProducts(); upsertEntries(e); upsertCustom(c); upsertProducts(p)
    }
}

/** Версия 2: жиры и углеводы у записей и таблица продуктов по штрих-коду. Старые записи не меняются. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN fat INTEGER")
        db.execSQL("ALTER TABLE entries ADD COLUMN carbs INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS products (code TEXT NOT NULL, name TEXT NOT NULL, brand TEXT NOT NULL, kcal REAL NOT NULL, " +
                "p REAL NOT NULL, f REAL NOT NULL, c REAL NOT NULL, packG INTEGER, source TEXT NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY(code))",
        )
    }
}

/** Версия 3: клетчатка у записей и продуктов. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN fiber REAL")
        db.execSQL("ALTER TABLE products ADD COLUMN fiber REAL NOT NULL DEFAULT 0")
    }
}

@Database(entities = [EntryEntity::class, CustomDishEntity::class, ProductEntity::class], version = 3, exportSchema = true)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun dao(): NutritionDao

    companion object {
        const val NAME = "nutrition.db"
        fun create(context: Context): NutritionDatabase = Room.databaseBuilder(context.applicationContext, NutritionDatabase::class.java, NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
