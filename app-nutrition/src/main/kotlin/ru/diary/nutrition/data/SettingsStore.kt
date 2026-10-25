package ru.diary.nutrition.data

import androidx.datastore.preferences.core.intPreferencesKey
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.diary.core.theme.ThemeMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("nutrition_settings")

/** Дневные нормы: калории, белок (нижняя граница), жиры, углеводы, клетчатка. */
data class Norms(val kcal: Int = 2500, val protein: Int = 105, val fat: Int = 80, val carbs: Int = 300, val fiber: Int = 30)

/** `onlineLookup` — искать продукты по штрих-коду в Open Food Facts (иначе только в своих продуктах). */
/** `photoAi` — распознавание фото блюда через Gemini API (выключено, пока пользователь сам не включит); `photoModel` — модель. */
data class Settings(
    val theme: ThemeMode = ThemeMode.System, val dynamicColor: Boolean = false, val onlineLookup: Boolean = true, val norms: Norms = Norms(),
    val photoAi: Boolean = false, val photoModel: String = GeminiRecognizer.MODELS.first().first,
)

/** Поле нормы и допустимый диапазон. */
enum class NormField(val min: Int, val max: Int, val step: Int) { Kcal(1000, 6000, 50), Protein(20, 300, 5), Fat(20, 250, 5), Carbs(50, 700, 10), Fiber(5, 80, 1) }

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val dynKey = booleanPreferencesKey("dynamic_color_v2")
    private val offKey = booleanPreferencesKey("online_lookup")
    private val photoKey = booleanPreferencesKey("photo_ai")
    private val photoModelKey = stringPreferencesKey("photo_model")
    private val normKeys = NormField.entries.associateWith { intPreferencesKey("norm_" + it.name.lowercase()) }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System, p[dynKey] ?: false, p[offKey] ?: true,
            Norms().let { d ->
                fun v(f: NormField, def: Int) = (p[normKeys.getValue(f)] ?: def).coerceIn(f.min, f.max)
                Norms(v(NormField.Kcal, d.kcal), v(NormField.Protein, d.protein), v(NormField.Fat, d.fat), v(NormField.Carbs, d.carbs), v(NormField.Fiber, d.fiber))
            },
            photoAi = p[photoKey] ?: false,
            photoModel = p[photoModelKey]?.takeIf { m -> GeminiRecognizer.MODELS.any { it.first == m } } ?: GeminiRecognizer.MODELS.first().first,
        )
    }

    suspend fun setTheme(m: ThemeMode) = context.dataStore.edit { it[themeKey] = m.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[dynKey] = v }
    suspend fun setOnlineLookup(v: Boolean) = context.dataStore.edit { it[offKey] = v }
    suspend fun setPhotoAi(v: Boolean) = context.dataStore.edit { it[photoKey] = v }
    suspend fun setPhotoModel(m: String) = context.dataStore.edit { it[photoModelKey] = m }
    suspend fun setNorm(f: NormField, v: Int) = context.dataStore.edit { it[normKeys.getValue(f)] = v.coerceIn(f.min, f.max) }
}
