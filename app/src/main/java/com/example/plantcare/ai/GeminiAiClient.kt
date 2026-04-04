package com.example.plantcare.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class GeminiAiClient(
    private val apiKey: String = DEFAULT_API_KEY,
    private val model: String = DEFAULT_MODEL
) : AiClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contents = JSONArray()

            if (!systemPrompt.isNullOrBlank()) {
                val part = JSONObject().put("text", systemPrompt)
                val partsArr = JSONArray().put(part)
                contents.put(JSONObject().put("role", "user").put("parts", partsArr))
            }

            history.forEach { entry ->
                val role = entry["role"] as? String ?: "user"
                val content = entry["content"] as? String ?: ""
                val geminiRole = if (role == "assistant") "model" else "user"
                val part = JSONObject().put("text", content)
                val partsArr = JSONArray().put(part)
                contents.put(JSONObject().put("role", geminiRole).put("parts", partsArr))
            }

            if (!userText.isNullOrBlank() || !imageBase64.isNullOrBlank()) {
                val partsArr = JSONArray()
                if (!imageBase64.isNullOrBlank()) {
                    val inlineData = JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", imageBase64)
                    partsArr.put(JSONObject().put("inline_data", inlineData))
                }
                if (!userText.isNullOrBlank()) {
                    partsArr.put(JSONObject().put("text", userText))
                } else {
                    partsArr.put(JSONObject().put("text", "Опиши что на фото растения"))
                }
                contents.put(JSONObject().put("role", "user").put("parts", partsArr))
            }

            val body = JSONObject()
                .put("contents", contents)
                .put("generationConfig", JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 2048)
                )

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.charStream()?.readText()
                if (!response.isSuccessful || responseBody == null) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${responseBody ?: "нет ответа"}")
                    )
                }
                val json = JSONObject(responseBody)
                if (json.has("candidates")) {
                    val text = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    return@withContext Result.success(text.trim())
                }
                if (json.has("error")) {
                    val msg = json.getJSONObject("error").optString("message", responseBody)
                    return@withContext Result.failure(Exception("Gemini error: $msg"))
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
        const val DEFAULT_MODEL = "gemini-2.0-flash"
        private const val DEFAULT_API_KEY = "YOUR_GEMINI_API_KEY"
    }
}
