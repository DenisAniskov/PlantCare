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
    private val apiKey: String = "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",
    private val baseUrl: String = "https://openrouter.ai",
    private val textModel: String = "nvidia/nemotron-nano-12b-v2-vl:free",
    private val visionModel: String = "nvidia/nemotron-nano-12b-v2-vl:free"
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
        val responseBody = response.body?.string()
        if (!response.isSuccessful || responseBody == null) {
            return Result.failure(Exception("HTTP ${response.code}: ${responseBody ?: "нет ответа"}"))
        }
        val json = JSONObject(responseBody)
        if (json.has("choices")) {
            val contentText = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            return Result.success(contentText.trim())
        }
        return Result.failure(Exception("Unexpected response: $responseBody"))
    }
}

class OpenRouterAiClientWithContext(private val context: Context) : AiClient {
    private val baseUrl = "https://openrouter.ai"

    companion object {
        const val TEXT_MODEL = "nvidia/nemotron-nano-12b-v2-vl:free"
        const val VISION_MODEL = "nvidia/nemotron-nano-12b-v2-vl:free"
    }

    private val apiKeys: List<String> by lazy {
        val customKey = Prefs.getOpenRouterApiKey(context)
        if (customKey.isNotBlank() && customKey != "YOUR_OPENROUTER_API_KEY") {
            listOf(customKey)
        } else {
            listOf(
                "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",
                "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",
                "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"
            )
        }
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
        val hasImage = !imageBase64.isNullOrBlank()
        val model = if (hasImage) VISION_MODEL else TEXT_MODEL
        val messages = buildMessages(history, userText, imageBase64, systemPrompt, hasImage)

        for (key in apiKeys) {
            try {
                val body = JSONObject(mapOf(
                    "model" to model,
                    "messages" to messages,
                    "temperature" to 0.7,
                    "max_tokens" to 2048
                )).toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/PlantCareProject")
                    .addHeader("X-Title", "PlantCare")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        return@withContext Result.success(content.trim())
                    } else if (response.code == 429) {
                        continue
                    } else {
                        val errorMsg = responseBody ?: "HTTP ${response.code}"
                        if (key == apiKeys.last()) return@withContext Result.failure(Exception(errorMsg))
                    }
                }
            } catch (e: Exception) {
                if (key == apiKeys.last()) return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception("Все API ключи исчерпаны или неверны"))
    }

    override suspend fun sendMessageStreaming(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        onChunk: (String) -> Unit
    ): Result<String> = sendMessage(history, userText, imageBase64, systemPrompt)

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
