package com.example.plantcare.ai

import com.example.plantcare.data.remote.ProxyClient
import com.example.plantcare.data.remote.model.*
import com.example.plantcare.db.ReferencePlantDao
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.io.IOException

class AiService(
    private val referencePlantDao: ReferencePlantDao,
    private val tfliteClassifier: PlantClassifier? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun chatWithFallback(
        prompt: String,
        imageBase64: String? = null,
        systemPrompt: String? = null,
        history: List<Map<String, String>>? = null
    ): Flow<String> = flow {
        val request = AiRequest(
            prompt = prompt,
            imageBase64 = imageBase64,
            systemPrompt = systemPrompt,
            history = history
        )

        // 1. Grok API (Primary)
        val grokResult = try {
            val response = ProxyClient.httpClient.post("/grok") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<AiResponse>()
            response.content
        } catch (e: Exception) {
            null
        }
        
        if (grokResult != null) {
            emit(grokResult)
            return@flow
        }

        // 2. Gemini API (Secondary)
        val geminiResult = try {
            val response = ProxyClient.httpClient.post("/gemini") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<AiResponse>()
            response.content
        } catch (e: Exception) {
            null
        }

        if (geminiResult != null) {
            emit(geminiResult)
            return@flow
        }

        // 3. OpenRouter (Final Cloud - Streaming)
        try {
            ProxyClient.httpClient.preparePost("/openrouter") {
                contentType(ContentType.Application.Json)
                setBody(request.copy(stream = true))
            }.execute { response ->
                val channel: ByteReadChannel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ")
                        if (data.trim() == "[DONE]") break
                        try {
                            val chunk = json.decodeFromString<ChatChunk>(data)
                            chunk.text?.let { emit(it) }
                        } catch (e: Exception) {
                            // Skip malformed chunks
                        }
                    }
                }
            }
            return@flow
        } catch (e: Exception) {
            // Fallback to local
        }

        // 4. Local Intelligence (Offline Fallback)
        if (imageBase64 != null && tfliteClassifier != null) {
            emit("Offline: Analyzing image locally...")
            val result = tfliteClassifier.classify(imageBase64)
            emit("\nLocal analysis: $result")
        } else {
            emit("Offline: Searching local database...")
            val localResults = referencePlantDao.searchReferencePlants("%$prompt%").first()
            if (localResults.isNotEmpty()) {
                emit("\nLocal info found: ${localResults.first().name}\n${localResults.first().description}")
            } else {
                emit("\nNo information found offline. Please check your connection.")
            }
        }
    }
}

interface PlantClassifier {
    fun classify(imageBase64: String): String
}
