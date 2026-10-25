package ru.diary.nutrition.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Полный цикл запроса к Gemini на локальном сервере: формат запроса, ключ в заголовке, разбор ответа и ошибок. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GeminiRecognizerTest {
    /** Минимальный HTTP-сервер на одно соединение: отдаёт заданный ответ и передаёт тело и ключ запроса в `check`. */
    private class OneShotServer(code: Int, body: String, check: (String, String?) -> Unit) : AutoCloseable {
        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/v1beta/interactions"
        private val thread = Thread {
            runCatching {
                socket.accept().use { c ->
                    val input = c.getInputStream().buffered()
                    val headers = HashMap<String, String>()
                    while (true) {
                        val line = readLine(input)
                        if (line.isEmpty()) break
                        val i = line.indexOf(':'); if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                    }
                    val len = headers["content-length"]?.toInt() ?: 0
                    val req = ByteArray(len); var read = 0
                    while (read < len) { val n = input.read(req, read, len - read); if (n < 0) break; read += n }
                    check(req.toString(Charsets.UTF_8), headers["x-goog-api-key"])
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val out = c.getOutputStream()
                    out.write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(bytes); out.flush()
                }
            }
        }.apply { start() }

        private fun readLine(input: java.io.InputStream): String {
            val sb = StringBuilder()
            while (true) {
                val ch = input.read()
                if (ch < 0 || ch == '\n'.code) break
                if (ch != '\r'.code) sb.append(ch.toChar())
            }
            return sb.toString()
        }

        override fun close() { thread.join(5000); socket.close() }
    }

    private fun serve(code: Int, body: String, check: (String, String?) -> Unit = { _, _ -> }) = OneShotServer(code, body, check)

    @Test fun sendsPhotoAndParsesAnswer() = runBlocking {
        val answer = """{"dish":"Омлет","items":[{"food":"omelet","grams":180}],"note":""}"""
        val resp = """{"object":"interaction","status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(answer))}}]}]}"""
        var seenKey: String? = null
        var request = ""
        val server = serve(200, resp) { body, key -> request = body; seenKey = key }
        server.use {
            val r = GeminiRecognizer(server.url).recognize(byteArrayOf(1, 2, 3), "test-key-1234567890", "gemini-3.8-flash")
            assertTrue(r is GeminiRecognizer.Result.Ok, r.toString())
            assertEquals("omelet", r.meal.items.single().food.id)
            assertEquals("test-key-1234567890", seenKey)
            val o = Json.parseToJsonElement(request).jsonObject
            assertEquals("gemini-3.8-flash", o["model"]!!.jsonPrimitive.content)
            val input = o["input"]!!.jsonArray
            assertEquals("image", input[1].jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("AQID", input[1].jsonObject["data"]!!.jsonPrimitive.content)
            assertEquals("application/json", o["response_format"]!!.jsonObject["mime_type"]!!.jsonPrimitive.content)
        }
    }

    @Test fun explainsErrors() = runBlocking {
        val cases = listOf(
            400 to """{"error":{"code":400,"message":"User location is not supported for the API use.","status":"FAILED_PRECONDITION"}}""" to "недоступен в вашей стране",
            400 to """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""" to "Ключ не подошёл",
            429 to """{"error":{"code":429,"message":"Resource has been exhausted","status":"RESOURCE_EXHAUSTED"}}""" to "лимит",
        )
        for ((pair, expected) in cases) {
            serve(pair.first, pair.second).use { server ->
                val r = GeminiRecognizer(server.url).recognize(byteArrayOf(1), "k".repeat(30), "gemini-3.8-flash")
                assertTrue(r is GeminiRecognizer.Result.Failed && r.message.contains(expected), "$expected ← $r")
            }
        }
    }
}
