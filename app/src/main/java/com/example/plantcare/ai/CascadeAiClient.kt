package com.example.plantcare.ai

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.plantcare.db.AppDatabase
import com.example.plantcare.db.ReferencePlantDao
import com.example.plantcare.util.Prefs
import com.example.plantcare.util.WikipediaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class CascadeAiClient(private val context: Context) : AiClient {
    
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "plantcare-db")
            .fallbackToDestructiveMigration()
            .build()
    }
    
    private val referencePlantDao: ReferencePlantDao by lazy {
        db.referencePlantDao()
    }
    
    private val plantDao: com.example.plantcare.db.PlantDao by lazy {
        db.plantDao()
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private var statusCallback: ((String) -> Unit)? = null
    private val statusHistory = mutableListOf<String>()

    override fun setStatusCallback(callback: (String) -> Unit) {
        statusCallback = callback
    }

    private fun updateStatus(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val fullMsg = "[$timestamp] $message"
        Log.d("CascadeAiClient", "Status: $fullMsg")
        statusHistory.add(fullMsg)
        statusCallback?.invoke(fullMsg)
    }

    suspend fun buildUserContextSystemPrompt(): String = withContext(Dispatchers.IO) {
        val contextBuilder = StringBuilder("Ты - эксперт-помощник по уходу за растениями. Помогай пользователю советами по выращиванию, лечению и уходу.\n")
        contextBuilder.append("\nВ конце ответа ВСЕГДА добавляй строку 'KEYWORDS: [English Plant Name, Suspected Disease/Problem in English]', чтобы я мог найти визуальные эталоны именно этой проблемы. Например: 'KEYWORDS: Potato, Late blight'.")
        contextBuilder.toString()
    }

    suspend fun getPlantRecommendations(plantName: String, plantType: String): Result<String> = withContext(Dispatchers.IO) {
        val prompt = """
            Дай подробные рекомендации по уходу за растением: $plantName ($plantType).
            Верни ответ СТРОГО в формате JSON без лишнего текста:
            {
              "watering_days": 3,
              "fertilizing_days": 14,
              "spraying_days": 2,
              "replanting_months": 12,
              "fertilizer_type": "Минеральные: (Названия), Органические: (Названия), Комплексные: (Названия)",
              "warning": "Краткое важное предупреждение"
            }
            Пиши РЕАЛЬНЫЕ числа. В 'fertilizer_type' подбери и перечисли через запятую конкретные названия удобрений, которые ЛУЧШЕ ВСЕГО подходят именно для этого растения ($plantName). Сгруппируй их по типам (Минеральные, Органические, Комплексные).
        """.trimIndent()

        val result = sendMessage(emptyList(), prompt, null, "Ты - ботанический справочник. Отвечай только чистым JSON.")
        
        result.map { content ->
            val startIndex = content.indexOf("{")
            val endIndex = content.lastIndexOf("}")
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                content.substring(startIndex, endIndex + 1)
            } else {
                content
            }
        }
    }

    private val stages = listOf(
        Stage(
            textModel = "nvidia/nemotron-3-super-120b-a12b:free",
            visionModel = "nvidia/nemotron-nano-12b-v2-vl:free",
            name = "Шаг 1: NVIDIA Nemotron"
        ),
        Stage(
            textModel = "minimax/minimax-m2.5:free",
            visionModel = "nvidia/nemotron-nano-12b-v2-vl:free",
            name = "Шаг 2: MiniMax"
        ),
        Stage(
            textModel = "z-ai/glm-4.5-air:free",
            visionModel = "nvidia/nemotron-nano-12b-v2-vl:free",
            name = "Шаг 3: GLM 4.5 Air"
        ),
        Stage(
            textModel = "openrouter/free",
            visionModel = "openrouter/free",
            name = "Шаг 4: OpenRouter Free"
        )
    )

    private val apiKeys: List<String> by lazy {
        val customKey = Prefs.getOpenRouterApiKey(context)
        if (customKey.isNotBlank() && customKey != "YOUR_OPENROUTER_API_KEY") {
            listOf(customKey)
        } else {
            listOf(
                "YOUR_OPENROUTER_API_KEY"
            )
        }
    }

    private val baseUrl = "https://openrouter.ai"

    private suspend fun performRequest(model: String, messages: List<Map<String, Any>>, temperature: Double = 0.7): String? {
        updateStatus("Запрос к модели: $model...")
        for (key in apiKeys) {
            val keyBrief = if (key.length > 10) key.take(6) + "..." + key.takeLast(4) else "invalid-key"
            try {
                val body = JSONObject(mapOf(
                    "model" to model,
                    "messages" to messages,
                    "temperature" to temperature
                )).toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("HTTP-Referer", "https://github.com/PlantCareProject")
                    .addHeader("X-Title", "PlantCare")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful && responseBody.isNotBlank()) {
                        val json = JSONObject(responseBody)
                        val messageObj = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                        val content = messageObj?.optString("content")
                        val reasoning = messageObj?.optString("reasoning")

                        if (content != null) {
                            updateStatus("Успех ($model)")
                            return if (!reasoning.isNullOrBlank()) {
                                "<thinking>\n$reasoning\n</thinking>\n$content"
                            } else {
                                content
                            }
                        } else {
                            updateStatus("Ошибка: Пустой контент в ответе от $model")
                        }
                    } else {
                        val errDetail = if (responseBody.length > 100) responseBody.take(100) + "..." else responseBody
                        updateStatus("Ошибка HTTP ${response.code} (Ключ: $keyBrief): $errDetail")
                        if (response.code == 429) {
                            updateStatus("Лимит запросов (429) для ключа $keyBrief. Пробую следующий...")
                            continue
                        }
                    }
                }
            } catch (e: Exception) {
                updateStatus("Исключение (Ключ $keyBrief): ${e.message}")
                if (e is java.net.UnknownHostException || e is java.net.ConnectException) {
                    updateStatus("Критическая ошибка сети: Нет доступа к OpenRouter")
                    return null
                }
            }
        }
        return null
    }

    private fun String.stripThinking(): String {
        return this.replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "").trim()
    }

    override suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val hasImage = !imageBase64.isNullOrBlank()
        var imageDescription: String? = null

        if (hasImage) {
            updateStatus("Анализируем изображение...")
            val visionPrompt = buildString {
                append("Ты — эксперт-биолог по визуальной диагностике. Твоя задача — составить краткий, но точный отчет об увиденном на фото для другой ИИ-модели.\n")
                append("Опиши:\n")
                append("1. Что за растение (или тип).\n")
                append("2. Состояние листьев (цвет, пятна, дыры, налет, увядание).\n")
                append("3. Состояние стебля и видимых частей.\n")
                append("4. Наличие вредителей (если видно).\n")
                append("5. Общее впечатление: здоровое, больное, дефицит элементов, стресс.\n")
                if (!userText.isNullOrBlank()) {
                    append("\nПользователь дополнительно спрашивает: ")
                    append(userText)
                    append("\nСфокусируйся на деталях, отвечающих на этот вопрос.")
                }
                append("\nПиши на русском, техническим языком, без вводных фраз.")
            }
            for (stage in stages) {
                val visionMessages = buildMessages(emptyList(), visionPrompt, imageBase64, "Ты — эксперт по визуальной диагностике растений. Твоя задача — дать максимально точное текстовое описание увиденного для другой ИИ-модели.", true)
                imageDescription = performRequest(stage.visionModel, visionMessages, 0.3)
                if (!imageDescription.isNullOrBlank()) {
                    updateStatus("Изображение проанализировано.")
                    break
                }
                kotlinx.coroutines.delay(300) // Небольшая задержка между попытками
            }
        }

        val finalUserText = buildString {
            if (!userText.isNullOrBlank()) append(userText)
            if (!imageDescription.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("--- КОНТЕКСТ ИЗ АНАЛИЗА ФОТО ---\n")
                append(imageDescription)
            }
        }.ifBlank { "Что на этом фото?" }

        val finalSystemPrompt = if (systemPrompt?.contains("JSON") == true) {
            (systemPrompt ?: "") + "\n\nВАЖНО: Твой ответ должен быть СТРОГО в формате JSON, как указано в инструкции. Не добавляй никаких рассуждений до или после JSON блока. Если нужны ссылки на Wikipedia, я добавлю их сам."
        } else {
            systemPrompt ?: buildUserContextSystemPrompt()
        }

        for (stage in stages) {
            try {
                updateStatus("Модель ${stage.name} думает...")
                val messages = buildMessages(history, finalUserText, null, finalSystemPrompt, false)
                val content = performRequest(stage.textModel, messages, 0.7)

                if (!content.isNullOrBlank()) {
                    // Если это JSON-запрос (из NeuralScreen), возвращаем СТРОГО чистый контент без рассуждений
                    if (systemPrompt?.contains("JSON") == true) {
                        val cleanJson = content.stripThinking()
                        return@withContext Result.success(cleanJson)
                    }

                    // Для обычного чата работаем с очищенным текстом для поиска ключевых слов
                    val cleanTextForKeywords = content.stripThinking()
                    val aiKeywords = extractAiKeywords(cleanTextForKeywords)
                    val finalKeywords = aiKeywords.ifEmpty { extractHeuristicKeywords(cleanTextForKeywords) }
                    
                    var finalResponse = content
                    if (finalKeywords.isNotEmpty()) {
                        updateStatus("Ищем справку для: ${finalKeywords.joinToString(", ")}...")
                        val wikiInfo = fetchWikipediaAndWikimedia(finalKeywords)
                        // Удаляем KEYWORDS из видимого текста, но сохраняем <thinking> если он есть
                        finalResponse = content.replace(Regex("(?i)KEYWORDS:.*"), "").trim() + "\n\n" + wikiInfo
                    }
                    
                    return@withContext Result.success(finalResponse.trim())
                }
                // Если модель не ответила, подождем чуть-чуть перед следующей, чтобы UI обновился
                kotlinx.coroutines.delay(500)
            } catch (e: Exception) {
                Log.e("CascadeAiClient", "Stage ${stage.name} failed", e)
            }
        }

        updateStatus("Все модели каскада не ответили.")
        val debugReport = buildString {
            append("⚠️ ОШИБКА ИИ-КАСКАДА\n")
            append("--------------------\n")
            statusHistory.forEach { append(it).append("\n") }
            append("--------------------\n")
            append("Использую оффлайн-режим...")
        }
        
        val localResult = getLocalFallback(finalUserText, imageBase64)
        return@withContext Result.success(debugReport + "\n\n" + localResult.getOrNull())
    }

    private fun extractAiKeywords(text: String): List<String> {
        val pattern = Regex("(?i)KEYWORDS:\\s*(.+)")
        return pattern.find(text)?.groupValues?.get(1)
            ?.split(",", ";")
            ?.map { it.trim().replace("[", "").replace("]", "").replace("\"", "") }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    private fun extractHeuristicKeywords(text: String): List<String> {
        val lowerText = text.lowercase()
        val plants = listOf(
            "картофе" to "Potato",
            "томат" to "Tomato",
            "огурец" to "Cucumber",
            "малин" to "Raspberry",
            "роза" to "Rose",
            "фиалк" to "Violet",
            "кактус" to "Cactus"
        )
        return plants.filter { lowerText.contains(it.first) }.map { it.second }.distinct()
    }

    private suspend fun fetchWikipediaAndWikimedia(keywords: List<String>): String = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        if (keywords.size > 1) {
            val combined = keywords.joinToString(" ")
            urls.addAll(fetchWikimediaImageList(combined))
        }
        if (urls.size < 2) {
            for (keyword in keywords.take(2)) {
                WikipediaApi.searchAndSummary(keyword).onSuccess { summary ->
                    if (summary.thumbnailUrl != null) urls.add(summary.thumbnailUrl)
                }
                urls.addAll(fetchWikimediaImageList(keyword))
            }
        }
        val distinctUrls = urls.distinct().take(4)
        if (distinctUrls.isNotEmpty()) {
            buildString {
                append("\n---\n🖼️ **Визуальные эталоны:**\n")
                distinctUrls.forEach { append("🔍 $it\n") }
            }
        } else ""
    }

    private fun fetchWikimediaImageList(keyword: String): List<String> {
        val images = mutableListOf<String>()
        try {
            val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=pageimages&generator=search&gsrsearch=$encoded&gsrlimit=3&gsrnamespace=6&piprop=thumbnail&pithumbsize=1000"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val pages = json.optJSONObject("query")?.optJSONObject("pages")
                        if (pages != null) {
                            val keys = pages.keys()
                            while (keys.hasNext()) {
                                val page = pages.getJSONObject(keys.next())
                                page.optJSONObject("thumbnail")?.optString("source")?.let { 
                                    images.add(it) 
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CascadeAiClient", "Wikimedia search failed for $keyword", e)
        }
        return images
    }

    private val localRagEngine by lazy {
        com.example.plantcare.core.LocalRagEngine(
            plantsJson = try { context.assets.open("plants.json").bufferedReader().use { it.readText() } } catch(_: Exception) { "[]" },
            pestsJson = try { context.assets.open("pests.json").bufferedReader().use { it.readText() } } catch(_: Exception) { "[]" },
            diseasesJson = try { context.assets.open("diseases.json").bufferedReader().use { it.readText() } } catch(_: Exception) { "[]" },
            tipsJson = try { context.assets.open("plant_care_tips.json").bufferedReader().use { it.readText() } } catch(_: Exception) { "[]" }
        )
    }

    private val plantClassifier by lazy { PlantClassifierImpl(context) }

    private suspend fun getLocalFallback(userText: String?, imageBase64: String?): Result<String> {
        val offlineHeader = "🔌 **Офлайн-режим (нет связи)**\n\n"
        val sb = StringBuilder(offlineHeader)

        if (!imageBase64.isNullOrBlank()) {
            sb.append("📸 **Анализ фото (TFLite):**\n")
            val classification = plantClassifier.classify(imageBase64)
            sb.append(classification).append("\n\n")
        }

        try {
            val fallback = localRagEngine
            val search = fallback.search(userText ?: "", limit = 3)
            if (search.isNotEmpty()) {
                sb.append("📖 **Информация из справочника:**\n\n")
                sb.append(search.joinToString("\n\n") { "**${it.title}**\n${it.text}" })
                return Result.success(sb.toString().trim())
            }
            
            fallback.fetchEntry(userText ?: "")?.let { (title, text) ->
                sb.append("📖 **Похожее из справочника:**\n\n**$title**\n$text")
                return Result.success(sb.toString().trim())
            }
        } catch (e: Exception) {
            Log.e("CascadeAiClient", "Local fallback failed", e)
        }
        
        try {
            val query = if (!userText.isNullOrBlank() && userText.length > 2) "%$userText%" else "%растение%"
            val localResults = referencePlantDao.searchReferencePlants(query).first()
            if (localResults.isNotEmpty()) {
                val plant = localResults.first()
                sb.append("📖 **${plant.name}**\n${plant.description}")
                return Result.success(sb.toString().trim())
            }
        } catch (e: Exception) { }

        if (sb.length <= offlineHeader.length + 30) { // Если ничего полезного не добавили кроме заголовка
            sb.append("К сожалению, я не смог найти ответ в локальной базе без интернета. Попробуйте сформулировать запрос иначе (например, только название растения).")
        }
        
        return Result.success(sb.toString().trim())
    }

    private fun buildMessages(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String,
        hasImage: Boolean
    ): List<Map<String, Any>> {
        return buildList {
            add(mapOf("role" to "system", "content" to systemPrompt))
            addAll(history)
            if (!userText.isNullOrBlank()) {
                if (hasImage) {
                    add(mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf("type" to "text", "text" to userText),
                            mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
                        )
                    ))
                } else {
                    add(mapOf("role" to "user", "content" to userText))
                }
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
        val result = sendMessage(history, userText, imageBase64, systemPrompt)
        if (result.isSuccess) {
            val fullText = result.getOrThrow()
            // Имитируем стриминг для плавности появления
            val words = fullText.split(Regex("(?<=\\s)|(?=\\s)"))
            var currentText = ""
            for (word in words) {
                currentText += word
                onChunk(currentText)
                kotlinx.coroutines.delay(15) 
            }
            return@withContext Result.success(fullText)
        } else {
            return@withContext result
        }
    }

    data class Stage(val textModel: String, val visionModel: String, val name: String)
}
