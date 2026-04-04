package com.example.plantcare.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FallbackAiClient(
    private val primary: AiClient,
    private val fallback: AiClient,
    private val lastResort: AiClient? = null
) : AiClient {
    override suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val hasImage = !imageBase64.isNullOrBlank()
        val orderedClients = if (hasImage) {
            listOf(fallback, primary, lastResort).filterNotNull()
        } else {
            listOf(primary, fallback, lastResort).filterNotNull()
        }

        for (client in orderedClients) {
            val result = client.sendMessage(history, userText, imageBase64, systemPrompt)
            if (result.isSuccess) return@withContext result
        }

        Result.failure(Exception("Нет подключения к интернету и сервисы недоступны"))
    }

    override suspend fun sendMessageStreaming(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        onChunk: (String) -> Unit
    ): Result<String> = sendMessage(history, userText, imageBase64, systemPrompt)
}
