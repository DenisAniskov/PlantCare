package com.example.plantcare.ai

import android.content.Context
import com.example.plantcare.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenRouterAiClient(
    private val apiKey: String = DEFAULT_API_KEY,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val textModel: String = TEXT_MODEL,
    private val visionModel: String = VISION_MODEL
) : AiClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val hasImage = !imageBase64.isNullOrBlank()
            val model = if (hasImage) visionModel else textModel

            val messages = buildMessages(history, userText, imageBase64, systemPrompt, hasImage)

            val body = JSONObject(mapOf(
                "model" to model,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 2048
            )).toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/PlantCareProject")
                .addHeader("X-Title", "PlantCare")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                parseResponse(response)
            }
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    override suspend fun sendMessageStreaming(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val hasImage = !imageBase64.isNullOrBlank()
            val model = if (hasImage) visionModel else textModel

            val messages = buildMessages(history, userText, imageBase64, systemPrompt, hasImage)

            val body = JSONObject(mapOf(
                "model" to model,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 2048,
                "stream" to true
            )).toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/PlantCareProject")
                .addHeader("X-Title", "PlantCare")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val buffer = StringBuilder()
            response.body?.charStream()?.forEachLine { line ->
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") return@forEachLine
                    try {
                        val json = JSONObject(data)
                        val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
                        if (!delta.isNullOrBlank()) {
                            buffer.append(delta)
                            onChunk(delta)
                        }
                    } catch (_: Exception) {}
                }
            }

            Result.success(buffer.toString().trim())
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    private fun buildMessages(history: List<Map<String, Any>>, userText: String?, imageBase64: String?, systemPrompt: String?, hasImage: Boolean): List<Map<String, Any>> {
        return buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(mapOf("role" to "system", "content" to systemPrompt))
            }
            addAll(history)
            if (!userText.isNullOrBlank()) {
                if (hasImage) {
                    val content = listOf(
                        mapOf("type" to "text", "text" to userText),
                        mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
                    )
                    add(mapOf("role" to "user", "content" to content))
                } else {
                    add(mapOf("role" to "user", "content" to userText))
                }
            } else if (hasImage) {
                val content = listOf(
                    mapOf("type" to "text", "text" to "Опиши что на фото растения"),
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
                )
                add(mapOf("role" to "user", "content" to content))
            }
        }
    }

    private fun parseResponse(response: okhttp3.Response): Result<String> {
        val responseBody = response.body?.charStream()?.readText()
        if (!response.isSuccessful || responseBody == null) {
            return Result.failure(Exception("HTTP ${response.code}: ${responseBody ?: "нет ответа"}"))
        }
        val json = JSONObject(responseBody)
        if (json.has("choices")) {
            val contentText = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            return Result.success(contentText.trim())
        }
        if (json.has("error")) {
            val msg = json.getJSONObject("error").optString("message", responseBody)
            return Result.failure(Exception("OpenRouter error: $msg"))
        }
        return Result.failure(Exception("Unexpected response"))
    }

    private fun mapError(e: Exception): Exception {
        val msg = e.message ?: ""
        val friendlyMsg = when {
            msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("ECONNREFUSED", ignoreCase = true) ||
            msg.contains("ENETUNREACH", ignoreCase = true) ->
                "Нет подключения к интернету. Проверьте соединение."
            msg.contains("timeout", ignoreCase = true) ->
                "Превышено время ожидания ответа от сервера."
            else -> "Ошибка: ${e.message}"
        }
        return Exception(friendlyMsg)
    }

    companion object {
        const val TEXT_MODEL = "openrouter/free"
        const val VISION_MODEL = "openrouter/free"
        private const val DEFAULT_API_KEY = "YOUR_OPENROUTER_API_KEY"
        private const val DEFAULT_BASE_URL = "https://openrouter.ai"
    }
}

class OpenRouterAiClientWithContext(private val context: Context) : AiClient {
    private val apiKey: String
    private val baseUrl = "https://openrouter.ai"

    companion object {
        const val TEXT_MODEL = "qwen/qwen3.6-plus:free"
        const val VISION_MODEL = "qwen/qwen3.6-plus:free"
    }

    init {
        apiKey = Prefs.getOpenRouterApiKey(context)
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("OpenRouter API ключ не настроен"))
        }

        try {
            val hasImage = !imageBase64.isNullOrBlank()
            val model = if (hasImage) VISION_MODEL else TEXT_MODEL

            val messages = buildMessages(history, userText, imageBase64, systemPrompt, hasImage)

            val body = JSONObject(mapOf(
                "model" to model,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 2048
            )).toString().toRequestBody("application/json".toMediaType())

            val url = "$baseUrl/api/v1/chat/completions"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/PlantCareProject")
                .addHeader("X-Title", "PlantCare")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.charStream()?.readText()
                if (!response.isSuccessful || responseBody == null) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${responseBody ?: "нет ответа"}"))
                }
                val json = JSONObject(responseBody)
                if (json.has("choices")) {
                    val contentText = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    return@withContext Result.success(contentText.trim())
                }
                if (json.has("error")) {
                    val msg = json.getJSONObject("error").optString("message", responseBody)
                    return@withContext Result.failure(Exception("OpenRouter error: $msg"))
                }
                return@withContext Result.failure(Exception("Unexpected response"))
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val friendlyMsg = when {
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("ECONNREFUSED", ignoreCase = true) ||
                msg.contains("ENETUNREACH", ignoreCase = true) ->
                    "Нет подключения к интернету. Проверьте соединение."
                msg.contains("timeout", ignoreCase = true) ->
                    "Превышено время ожидания ответа от сервера."
                else -> "Ошибка: ${e.message}"
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    override suspend fun sendMessageStreaming(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("OpenRouter API ключ не настроен"))
        }

        try {
            val hasImage = !imageBase64.isNullOrBlank()
            val model = "openrouter/free"

            val messages = buildMessages(history, userText, imageBase64, systemPrompt, hasImage)

            val body = JSONObject(mapOf(
                "model" to model,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 2048,
                "stream" to true
            )).toString().toRequestBody("application/json".toMediaType())

            val url = "$baseUrl/api/v1/chat/completions"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/PlantCareProject")
                .addHeader("X-Title", "PlantCare")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val buffer = StringBuilder()
            response.body?.charStream()?.forEachLine { line ->
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") return@forEachLine
                    try {
                        val json = JSONObject(data)
                        val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
                        if (!delta.isNullOrBlank()) {
                            buffer.append(delta)
                            onChunk(delta)
                        }
                    } catch (_: Exception) {}
                }
            }

            Result.success(buffer.toString().trim())
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val friendlyMsg = when {
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("ECONNREFUSED", ignoreCase = true) ||
                msg.contains("ENETUNREACH", ignoreCase = true) ->
                    "Нет подключения к интернету. Проверьте соединение."
                msg.contains("timeout", ignoreCase = true) ->
                    "Превышено время ожидания ответа от сервера."
                else -> "Ошибка: ${e.message}"
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    private fun buildMessages(history: List<Map<String, Any>>, userText: String?, imageBase64: String?, systemPrompt: String?, hasImage: Boolean): List<Map<String, Any>> {
        return buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(mapOf("role" to "system", "content" to systemPrompt))
            }
            addAll(history)
            if (!userText.isNullOrBlank()) {
                if (hasImage) {
                    val content = listOf(
                        mapOf("type" to "text", "text" to userText),
                        mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
                    )
                    add(mapOf("role" to "user", "content" to content))
                } else {
                    add(mapOf("role" to "user", "content" to userText))
                }
            } else if (hasImage) {
                val content = listOf(
                    mapOf("type" to "text", "text" to "Опиши что на фото растения"),
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
                )
                add(mapOf("role" to "user", "content" to content))
            }
        }
    }
}