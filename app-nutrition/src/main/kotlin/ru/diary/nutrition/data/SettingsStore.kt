package ru.diary.nutrition.data

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

data class Settings(val theme: ThemeMode = ThemeMode.System, val dynamicColor: Boolean = true)

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val dynKey = booleanPreferencesKey("dynamic_color")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System, p[dynKey] ?: true)
    }

    suspend fun setTheme(m: ThemeMode) = context.dataStore.edit { it[themeKey] = m.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[dynKey] = v }
}
