package ru.diary.training.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.diary.core.theme.ThemeMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("training_settings")

/** Настройки приложения. */
data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    /** Автоматический отдых: берётся значение упражнения, иначе `restLen`. */
    val restAuto: Boolean = true,
    val restLen: Int = 90,
    val notificationsAsked: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val restAuto = booleanPreferencesKey("rest_auto")
        val restLen = intPreferencesKey("rest_len")
        val notifAsked = booleanPreferencesKey("notifications_asked")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            dynamicColor = p[Keys.dynamic] ?: true,
            restAuto = p[Keys.restAuto] ?: true,
            restLen = (p[Keys.restLen] ?: 90).coerceIn(5, 3600),
            notificationsAsked = p[Keys.notifAsked] ?: false,
        )
    }

    suspend fun setTheme(m: ThemeMode) = context.dataStore.edit { it[Keys.theme] = m.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.dynamic] = v }
    suspend fun setRestAuto(v: Boolean) = context.dataStore.edit { it[Keys.restAuto] = v }
    suspend fun setRestLen(sec: Int) = context.dataStore.edit { it[Keys.restLen] = sec.coerceIn(5, 3600) }
    suspend fun setNotificationsAsked() = context.dataStore.edit { it[Keys.notifAsked] = true }
}
