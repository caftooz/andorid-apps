package ru.diary.nutrition.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.diary.nutrition.domain.OffResult
import ru.diary.nutrition.domain.OpenFoodFacts
import java.net.HttpURLConnection
import java.net.URL

/**
 * Поиск продукта по штрих-коду в Open Food Facts. Наружу уходит только номер штрих-кода.
 * Любая сетевая ошибка превращается в [Lookup.Failed], и пользователь вводит КБЖУ вручную.
 */
class ProductLookup(private val appVersion: String) {
    sealed interface Lookup {
        data class Done(val result: OffResult) : Lookup
        data class Failed(val reason: String) : Lookup
    }

    suspend fun find(code: String, now: Long): Lookup = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(OpenFoodFacts.url(code)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                instanceFollowRedirects = true
                // Open Food Facts просит указывать приложение в User-Agent.
                setRequestProperty("User-Agent", "Racion/$appVersion (Android; https://github.com/caftooz/andorid-apps)")
                setRequestProperty("Accept", "application/json")
            }
            when (val status = conn.responseCode) {
                200 -> Lookup.Done(OpenFoodFacts.parse(code, conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }, now))
                404 -> Lookup.Done(OffResult.NotFound)
                else -> Lookup.Failed("сервер ответил $status")
            }
        } catch (e: Exception) {
            Lookup.Failed("нет связи")
        } finally {
            conn?.disconnect()
        }
    }
}
