package ru.diary.nutrition.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Обновление базы 1 → 2 сохраняет записи дневника и добавляет пустую таблицу продуктов. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NutritionDatabase::class.java)

    @Test fun migrate1To2() {
        helper.createDatabase("m.db", 1).use { db ->
            db.execSQL("INSERT INTO entries (id, date, time, dish, name, portion, kcal, protein, plan) VALUES ('a', '2026-10-01', '09:00', 'omelet', 'Омлет', 1.0, 350, 22, 1)")
        }
        helper.runMigrationsAndValidate("m.db", 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT name, kcal, fat, carbs FROM entries").use { c ->
                c.moveToFirst()
                assertEquals("Омлет", c.getString(0)); assertEquals(350, c.getInt(1))
                assertEquals(true, c.isNull(2)); assertEquals(true, c.isNull(3))
            }
            db.query("SELECT COUNT(*) FROM products").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }
}
