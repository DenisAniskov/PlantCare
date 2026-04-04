package com.example.plantcare.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RemoteAiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val apiKey: String = DEFAULT_API_KEY,
    private val model: String = DEFAULT_MODEL
) : AiClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = systemPrompt?.takeIf { it.isNotBlank() }
        try {
            val content = mutableListOf<Map<String, Any>>()
            if (!userText.isNullOrBlank()) {
                content.add(mapOf("type" to "text", "text" to userText))
            }
            if (!imageBase64.isNullOrBlank()) {
                content.add(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64")
                    )
                )
            }
            val newUserMsg = mapOf("role" to "user", "content" to content)
            val messages = buildList {
                if (!prompt.isNullOrBlank()) add(mapOf("role" to "system", "content" to prompt))
                addAll(history)
                add(newUserMsg)
            }

            val body = JSONObject(mapOf(
                "model" to model,
                "messages" to messages
            )).toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.charStream()?.readText()
                if (!response.isSuccessful || responseBody == null) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
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
                    return@withContext Result.failure(Exception("API error: $msg"))
                }
                return@withContext Result.failure(Exception("Unexpected response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
        // LM Studio (локальная сеть)
        private const val DEFAULT_BASE_URL = "http://192.168.1.126:1234"
        private const val DEFAULT_API_KEY = "local-key"
        private const val DEFAULT_MODEL = "google/gemma-3-12b"
        
        // Для публичного API (OpenAI, Groq и др.) замените на:
        // private const val DEFAULT_BASE_URL = "https://api.openai.com"
        // private const val DEFAULT_API_KEY = "sk-your-api-key-here"
        // private const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
