package com.example.plantcare.ai

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.plantcare.db.AppDatabase
import com.example.plantcare.db.ReferencePlantDao
import com.example.plantcare.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CascadeAiClient(private val context: Context) : AiClient {
    
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "plantcare-db").build()
    }
    
    private val referencePlantDao: ReferencePlantDao by lazy {
        db.referencePlantDao()
    }
    
    private val stages = listOf(
        Stage(
            textModel = "qwen/qwen3.6-plus:free",
            visionModel = "qwen/qwen3.6-plus:free",
            name = "Qwen 3.6"
        ),
        Stage(
            textModel = "minimax/minimax-m2.5:free",
            visionModel = "nvidia/nemotron-3-nano-12b-vl:free",
            name = "MiniMax + NVIDIA",
            hasVisionPriority = true // For images, try vision model first, then text
        ),
        Stage(
            textModel = "stepfun/step-3.5-flash:free",
            visionModel = "google/gemma-3-4b-it:free",
            name = "StepFun + Gemma"
        ),
        Stage(
            textModel = "openrouter/free",
            visionModel = "openrouter/free",
            name = "OpenRouter Default"
        )
    )

    private var statusCallback: ((String) -> Unit)? = null
    private val statusHistory = mutableListOf<String>()
    private val chatHistoryFile = File(context.filesDir, "chat_history.json")

    private val apiKey: String = Prefs.getOpenRouterApiKey(context)
    private val baseUrl = "https://openrouter.ai"
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    override fun setStatusCallback(callback: (String) -> Unit) {
        statusCallback = callback
    }

    private fun updateStatus(message: String) {
        Log.d("CascadeAiClient", "Status: $message")
        statusHistory.add(message)
        statusCallback?.invoke(message)
    }
    
    fun getStatusHistory(): List<String> = statusHistory.toList()
    
    fun clearStatusHistory() {
        statusHistory.clear()
    }

    override suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext getLocalFallback(userText)
        }

        val hasImage = !imageBase64.isNullOrBlank()
        
        // Add image indicator to userText if image is attached
        val enhancedUserText = if (hasImage && !userText.isNullOrBlank()) {
            "{фото прикреплено} $userText"
        } else if (hasImage && userText.isNullOrBlank()) {
            "{фото прикреплено}"
        } else {
            userText
        }

        for (stage in stages) {
            val model = selectModelForStage(stage, hasImage)
            
            try {
                updateStatus("Модель ${stage.name} думает, подождите...")
                kotlinx.coroutines.delay(500) // Give UI time to show status
                
                val messages = buildMessages(history, enhancedUserText, imageBase64, systemPrompt, hasImage)
                
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
                    val responseBody = response.body?.string()
                    
                    // Handle specific error codes
                    when (response.code) {
                        429 -> {
                            updateStatus("У модели ${stage.name} сильная нагрузка, пробую запасную...")
                            kotlinx.coroutines.delay(800)
                            return@use
                        }
                        in 500..599 -> {
                            updateStatus("Ошибка сервера (${response.code}), пробую запасную...")
                            kotlinx.coroutines.delay(800)
                            return@use
                        }
                    }
                    
                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        if (json.has("choices")) {
                            val content = json.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            
                            // Try to fetch images for plant mentions
                            val (plantImages, wikiLog) = fetchWikimediaImagesWithLog(content)
                            var finalContent = if (plantImages.isNotEmpty()) {
                                "$content\n\n$plantImages"
                            } else {
                                content
                            }
                            
                            // Remove KEYWORDS line from displayed content
                            finalContent = finalContent.replace(Regex("(?i)\\n?\\s*KEYWORDS:\\s*.+", RegexOption.IGNORE_CASE), "")
                            
                            // Log issues and show in UI for debugging - only if no images found
                            val isDebug = context.getSharedPreferences("plantcare_prefs", Context.MODE_PRIVATE).getBoolean("debug_mode", false)
                            if (wikiLog.isNotBlank() && plantImages.isBlank() && isDebug) {
                                Log.w("CascadeAiClient", "Image search failed: $wikiLog")
                                finalContent = "$finalContent\n\n⚙️ $wikiLog"
                            }
                            
                            val userMsg = mapOf("role" to "user", "content" to (enhancedUserText ?: ""))
                            val assistantMsg = mapOf("role" to "assistant", "content" to finalContent)
                            saveChatHistory(history + userMsg + assistantMsg)
                            return@withContext Result.success(finalContent.trim())
                        } else if (json.has("error")) {
                            val errorMsg = json.getJSONObject("error").optString("message", "Unknown error")
                            updateStatus("Ошибка ${stage.name}: $errorMsg")
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: ""
                when {
                    errorMsg.contains("Unable to resolve host", ignoreCase = true) -> {
                        updateStatus("Проверьте доступность интернета")
                        kotlinx.coroutines.delay(1000)
                    }
                    errorMsg.contains("timeout", ignoreCase = true) -> {
                        updateStatus("Превышен таймаут, пробую запасную...")
                        kotlinx.coroutines.delay(800)
                    }
                    else -> {
                        updateStatus("Ошибка ${stage.name}: $errorMsg")
                        kotlinx.coroutines.delay(500)
                    }
                }
            }
        }

        updateStatus("Перехожу в оффлайн-режим...")
        kotlinx.coroutines.delay(800)
        return@withContext getLocalFallback(userText)
    }

    private fun selectModelForStage(stage: Stage, hasImage: Boolean): String {
        if (!hasImage) return stage.textModel
        
        // For stage 2 (MiniMax + NVIDIA), handle specially
        if (stage.hasVisionPriority) {
            // First try vision model, if fails will fall back to text model
            return stage.visionModel
        }
        
        return stage.visionModel
    }

    private suspend fun getLocalFallback(userText: String?): Result<String> {
        try {
            val query = if (!userText.isNullOrBlank() && userText.length > 2) "%$userText%" else "%растение%"
            val localResults = referencePlantDao.searchReferencePlants(query).first()
            
            if (localResults.isNotEmpty()) {
                val plant = localResults.first()
                val response = buildString {
                    appendLine("Оффлайн-режим: 📚 Информация из локальной базы:")
                    appendLine()
                    appendLine("**${plant.name}**")
                    if (plant.description.isNotBlank()) {
                        appendLine(plant.description)
                    }
                    if (!plant.watering.isNullOrBlank()) {
                        appendLine()
                        appendLine("💧 Полив: ${plant.watering}")
                    }
                    if (!plant.light.isNullOrBlank()) {
                        appendLine("☀️ Свет: ${plant.light}")
                    }
                    if (!plant.temperature.isNullOrBlank()) {
                        appendLine("🌡️ Температура: ${plant.temperature}")
                    }
                    if (!plant.fertilizing.isNullOrBlank()) {
                        appendLine("🌱 Удобрение: ${plant.fertilizing}")
                    }
                    if (!plant.notes.isNullOrBlank()) {
                        appendLine()
                        appendLine("📝 ${plant.notes}")
                    }
                }
                return Result.success(response)
            } else {
                return Result.failure(Exception("Оффлайн-режим: в локальной базе ничего не найдено по вашему запросу \"$userText\""))
            }
        } catch (e: Exception) {
            Log.e("CascadeAiClient", "Local DB error: ${e.message}", e)
        }
        
        return Result.failure(Exception("Все модели недоступны. Проверьте подключение к интернету."))
    }

    private fun fetchWikimediaImagesWithLog(text: String): Pair<String, String> {
        val keywords = extractPlantKeywords(text)
        if (keywords.isEmpty()) {
            return Pair("", "KEYWORDS: не найдены в ответе ИИ")
        }
        
        val logs = StringBuilder("KEYWORDS: $keywords")
        
        return try {
            val images = mutableListOf<String>()
            for (keyword in keywords.take(2)) {
                val cleanKeyword = keyword.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
                if (cleanKeyword.isEmpty()) continue
                
                val encodedQuery = java.net.URLEncoder.encode(cleanKeyword, "UTF-8")
                // Using Wikimedia Commons API - specifically searching for FILES (namespace 6) with relevance sorting
                val urlStr = "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=pageimages&generator=search&gsrsearch=$encodedQuery&gsrlimit=3&gsrnamespace=6&piprop=thumbnail&pithumbsize=1000&gsrsort=relevance"
                logs.append(" | URL: $urlStr")
                
                val request = Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", "PlantCare/1.0 (https://github.com/PlantCareProject; contact@example.com) OkHttp/4.12")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    logs.append(" | Response: ${response.code}")
                    
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            val query = json.optJSONObject("query")
                            val pages = query?.optJSONObject("pages")
                            
                            if (pages != null) {
                                val keys = pages.keys()
                                while (keys.hasNext()) {
                                    val pageId = keys.next()
                                    val page = pages.getJSONObject(pageId)
                                    val thumbnail = page.optJSONObject("thumbnail")
                                    val imgUrl = thumbnail?.optString("source")
                                    if (imgUrl != null) {
                                        val title = page.optString("title", "")
                                        // Wikimedia links use underscores for spaces and specific formatting
                                        val wikiTitle = title.replace(" ", "_")
                                        val wikiUrl = "https://commons.wikimedia.org/wiki/$wikiTitle"
                                        images.add("🔍 $imgUrl\n📎 $wikiUrl (Wikimedia)")
                                    }
                                }
                            }
                        }
                    } else {
                        logs.append(" | Error: ${response.message}")
                    }
                }
            }
            
            if (images.isNotEmpty()) {
                val result = buildString {
                    appendLine("🖼️ Эталонные изображения (Wikimedia Commons):")
                    images.forEach { appendLine(it) }
                    appendLine("📝 Лицензия: CC BY-SA / Public Domain")
                }
                Pair(result, "")
            } else {
                logs.append(" | Result: изображений не найдено")
                Pair("", logs.toString())
            }
        } catch (e: Exception) {
            val errorLog = "${logs} | ${e.javaClass.simpleName}: ${e.message}"
            Pair("", errorLog)
        }
    }

    private fun fetchPixabayImages(text: String): String {
        return fetchWikimediaImagesWithLog(text).first
    }

    private fun extractPlantKeywords(text: String): List<String> {
        val keywords = mutableListOf<String>()
        
        // Match KEYWORDS: followed by tags (handles brackets [tag1, tag2] or plain tag1, tag2)
        val keywordPattern = Regex("""KEYWORDS:\s*(.+)""", RegexOption.IGNORE_CASE)
        keywordPattern.find(text)?.let { match ->
            val raw = match.groupValues[1]
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .replace("'", "")
                .split(Regex("[\n\r]"))[0] // Only take the first line
            
            raw.split(",", ";").forEach { kw ->
                val trimmed = kw.trim()
                if (trimmed.isNotBlank()) {
                    keywords.add(trimmed.lowercase())
                }
            }
            return keywords
        }
        
        val diseasePatterns = listOf(
            "мучнистая роса" to "powdery mildew",
            "фитофтороз" to "phytophthora blight",
            "серая гниль" to "botrytis gray mold",
            "черная ножка" to "damping off",
            "ржавчина" to "plant rust fungus",
            "антракноз" to "anthracnose fungus",
            "мозаика" to "mosaic virus",
            "хлороз" to "chlorosis plant",
            "корневая гниль" to "root rot",
            "пятнистость листьев" to "leaf spot disease",
            "парша" to "plant scab disease",
            "фузариоз" to "fusarium wilt",
            "вертициллез" to "verticillium wilt",
            "бактериоз" to "bacterial spot",
            "ложная мучнистая роса" to "downy mildew",
            "альтернариоз" to "alternaria leaf spot",
            "септориоз" to "septoria leaf spot",
            "кладоспориоз" to "cladosporium leaf spot",
            "склеротиниоз" to "sclerotinia rot",
            "бронзовость" to "tomato spotted wilt virus"
        )
        
        val lowerText = text.lowercase()
        for ((russian, english) in diseasePatterns) {
            if (lowerText.contains(russian)) {
                keywords.add(english)
            }
        }
        
        val plantPatterns = listOf(
            "томат" to "tomato plant",
            "огурец" to "cucumber plant",
            "перец" to "pepper plant",
            "картофель" to "potato plant",
            "капуста" to "cabbage plant",
            "морковь" to "carrot plant",
            "лук" to "onion plant",
            "чеснок" to "garlic plant",
            "клубника" to "strawberry plant",
            "малина" to "raspberry plant",
            "смородина" to "currant plant",
            "яблоня" to "apple tree",
            "груша" to "pear tree",
            "вишня" to "cherry tree",
            "слива" to "plum tree",
            "роза" to "rose flower",
            "тюльпан" to "tulip flower",
            "лилия" to "lily flower",
            "петуния" to "petunia flower",
            "фиалка" to "violet flower"
        )
        
        for ((russian, english) in plantPatterns) {
            if (lowerText.contains(russian)) {
                keywords.add(english)
            }
        }
        
        return keywords.distinct().take(3)
    }

    private fun saveChatHistory(messages: List<Map<String, Any>>) {
        try {
            val json = JSONObject()
            json.put("messages", messages.map { JSONObject(it) })
            json.put("timestamp", System.currentTimeMillis())
            chatHistoryFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.e("CascadeAiClient", "Failed to save history: ${e.message}")
        }
    }

    fun loadChatHistory(): List<Map<String, Any>>? {
        return try {
            if (chatHistoryFile.exists()) {
                val json = JSONObject(chatHistoryFile.readText())
                val messagesArray = json.getJSONArray("messages")
                (0 until messagesArray.length()).map { i ->
                    val msg = messagesArray.getJSONObject(i)
                    msg.keys().asSequence().associateWith { key -> msg.get(key).toString() }
                }
            } else null
        } catch (e: Exception) {
            Log.e("CascadeAiClient", "Failed to load history: ${e.message}")
            null
        }
    }

    fun clearChatHistory() {
        try {
            if (chatHistoryFile.exists()) {
                chatHistoryFile.delete()
            }
        } catch (e: Exception) {
            Log.e("CascadeAiClient", "Failed to clear history: ${e.message}")
        }
    }

    // RAG Placeholder - for future implementation
    suspend fun searchLocalFiles(query: String): String? {
        // Placeholder for RAG functionality
        // Would search through local PDF/text files in future
        return null
    }

    private fun buildMessages(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        hasImage: Boolean
    ): List<Map<String, Any>> {
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

    override suspend fun sendMessageStreaming(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        sendMessage(history, userText, imageBase64, systemPrompt)
    }

    data class Stage(
        val textModel: String,
        val visionModel: String,
        val name: String,
        val hasVisionPriority: Boolean = false
    )
}
