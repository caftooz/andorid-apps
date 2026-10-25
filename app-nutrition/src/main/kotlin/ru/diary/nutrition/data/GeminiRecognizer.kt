package ru.diary.nutrition.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import ru.diary.nutrition.domain.PhotoMeal
import ru.diary.nutrition.domain.PhotoPrompt
import java.net.HttpURLConnection
import java.net.URL

/**
 * Распознавание фото блюда через Google Gemini API (Interactions API) по ключу пользователя.
 * Наружу уходят только уменьшенное фото и текст запроса со списком продуктов; ответ — JSON по схеме [PhotoPrompt.schema].
 */
class GeminiRecognizer(private val endpoint: String = ENDPOINT) {
    sealed interface Result {
        data class Ok(val meal: PhotoMeal) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun recognize(jpeg: ByteArray, apiKey: String, model: String): Result = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("input") {
                addJsonObject { put("type", "text"); put("text", PhotoPrompt.instruction()) }
                addJsonObject { put("type", "image"); put("mime_type", "image/jpeg"); put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)) }
            }
            putJsonObject("response_format") {
                put("type", "text"); put("mime_type", "application/json"); put("schema", PhotoPrompt.schema())
            }
        }.toString()
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 15_000; readTimeout = 90_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) return@withContext Result.Failed(httpError(code, text))
            val out = outputText(text) ?: return@withContext Result.Failed("Модель не вернула ответ. Попробуйте ещё раз.")
            val meal = PhotoPrompt.parse(out) ?: return@withContext Result.Failed("Не удалось разобрать ответ модели. Попробуйте ещё раз.")
            Result.Ok(meal)
        } catch (e: java.net.SocketTimeoutException) {
            Result.Failed("Сервер долго не отвечает. Попробуйте ещё раз.")
        } catch (e: Exception) {
            Result.Failed("Нет связи с Gemini API.")
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        /** Модели, которые Google рекомендует для новых проектов; первая — по умолчанию. */
        val MODELS = listOf("gemini-3.8-flash" to "3.8 Flash", "gemini-3.5-flash-lite" to "3.5 Flash-Lite")

        private val json = Json { ignoreUnknownKeys = true }

        /** Текст ответа: `steps[].content[].text` у шага `model_output` (на всякий случай понимает и `output_text`). */
        fun outputText(body: String): String? {
            val o = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            (o["output_text"] as? JsonPrimitive)?.contentOrNull?.let { return it }
            val parts = (o["steps"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
                .filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "model_output" }
                .flatMap { (it["content"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>() }
                .filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "text" }
                .mapNotNull { (it["text"] as? JsonPrimitive)?.contentOrNull }
            return parts.joinToString("").takeIf { it.isNotBlank() }
        }

        /** Понятное сообщение по ошибке API: регион, ключ, лимиты. */
        fun httpError(code: Int, body: String): String {
            val msg = runCatching { ((json.parseToJsonElement(body) as JsonObject)["error"] as JsonObject)["message"] as JsonPrimitive }.getOrNull()?.contentOrNull.orEmpty()
            return when {
                msg.contains("location", ignoreCase = true) -> "Gemini API недоступен в вашей стране."
                code == 400 && msg.contains("API key", ignoreCase = true) || code == 401 || code == 403 -> "Ключ не подошёл. Проверьте его в Google AI Studio."
                code == 429 -> "Бесплатный лимит запросов исчерпан. Попробуйте позже — дневной лимит обновляется ночью по тихоокеанскому времени."
                code == 404 -> "Модель недоступна для этого ключа. Выберите другую в настройках."
                code >= 500 -> "Сервис Gemini временно недоступен. Попробуйте позже."
                msg.isNotEmpty() -> "Ошибка Gemini API: $msg"
                else -> "Ошибка Gemini API ($code)."
            }
        }
    }
}
