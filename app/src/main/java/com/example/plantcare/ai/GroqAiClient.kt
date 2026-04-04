package com.example.plantcare.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqAiClient(
    private val apiKey: String = DEFAULT_API_KEY,
    private val textModel: String = TEXT_MODEL,
    private val visionModel: String = VISION_MODEL
) : AiClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
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

            val messages = buildList {
                if (!systemPrompt.isNullOrBlank()) {
                    add(mapOf("role" to "system", "content" to systemPrompt))
                }
                addAll(history)
                if (!userText.isNullOrBlank()) {
                    if (hasImage) {
                        val content = listOf(
                            mapOf("type" to "text", "text" to userText),
                            mapOf(
                                "type" to "image_url",
                                "image_url" to mapOf(
                                    "url" to "data:image/jpeg;base64,$imageBase64"
                                )
                            )
                        )
                        add(mapOf("role" to "user", "content" to content))
                    } else {
                        add(mapOf("role" to "user", "content" to userText))
                    }
                } else if (hasImage) {
                    val content = listOf(
                        mapOf("type" to "text", "text" to "Опиши что на фото растения"),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf(
                                "url" to "data:image/jpeg;base64,$imageBase64"
                            )
                        )
                    )
                    add(mapOf("role" to "user", "content" to content))
                }
            }

            val body = JSONObject(mapOf(
                "model" to model,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 2048
            )).toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.charStream()?.readText()
                if (!response.isSuccessful || responseBody == null) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${responseBody ?: "нет ответа"}")
                    )
                }
                val json = JSONObject(responseBody)
                if (json.has("choices")) {
                    val contentText = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    return@withContext Result.success(contentText.trim())
                }
                if (json.has("error")) {
                    val msg = json.getJSONObject("error").optString("message", responseBody)
                    return@withContext Result.failure(Exception("Groq error: $msg"))
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
    ): Result<String> = sendMessage(history, userText, imageBase64, systemPrompt)

    companion object {
        const val TEXT_MODEL = "llama-3.1-8b-instant"
        const val VISION_MODEL = "llama-3.2-11b-vision-preview"
        private const val DEFAULT_API_KEY = "YOUR_GROQ_API_KEY"
        private const val DEFAULT_BASE_URL = "https://api.groq.com"
    }
}
