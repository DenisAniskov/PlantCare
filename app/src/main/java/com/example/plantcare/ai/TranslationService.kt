package com.example.plantcare.ai

import android.content.Context
import android.util.Log
import com.example.plantcare.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TranslationService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val models = listOf(
        "google/gemini-2.0-flash-exp:free",
        "nvidia/nemotron-3-super-120b-a12b:free",
        "meta-llama/llama-3.1-8b-instruct:free",
        "minimax/minimax-m2.5:free"
    )

    private fun getApiKeys(context: Context?): List<String> {
        val customKey = context?.let { Prefs.getOpenRouterApiKey(it) }
        if (!customKey.isNullOrBlank() && customKey != "YOUR_OPENROUTER_API_KEY") {
            return listOf(customKey)
        }
        return listOf(
            "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",
            "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",
            "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"
        )
    }

    private suspend fun callCascade(prompt: String, context: Context?, isJson: Boolean = false): String? = withContext(Dispatchers.IO) {
        val keys = getApiKeys(context)
        
        for (modelName in models) {
            for (apiKey in keys) {
                try {
                    val bodyJson = JSONObject().apply {
                        put("model", modelName)
                        put("messages", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }))
                        put("temperature", 0.1)
                        if (isJson) {
                            put("response_format", JSONObject().put("type", "json_object"))
                        }
                    }

                    val request = Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("HTTP-Referer", "https://github.com/PlantCareProject")
                        .addHeader("X-Title", "PlantCare")
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: return@use
                        if (response.isSuccessful) {
                            val content = JSONObject(body).getJSONArray("choices")
                                .getJSONObject(0).getJSONObject("message").getString("content").trim()
                            return@withContext cleanResponse(content)
                        } else {
                            Log.e("TranslationService", "API Error ${response.code}: $body")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TranslationService", "Model $modelName failed: ${e.message}")
                }
            }
        }
        null
    }

    private fun cleanResponse(text: String): String {
        var res = text.trim()
        
        // 1. Удаляем Markdown блоки ```json ... ``` (включая многострочные)
        val markdownRegex = Regex("(?s)```(?:json)?\\s*(.*?)\\s*```")
        val match = markdownRegex.find(res)
        if (match != null) {
            res = match.groupValues[1]
        }

        // 2. Если ожидается JSON, но есть мусор вокруг, вырезаем по скобкам
        if (res.contains("{") && res.contains("}")) {
            val start = res.indexOf("{")
            val end = res.lastIndexOf("}")
            if (start < end) res = res.substring(start, end + 1)
        }
        
        // 3. Убираем вводные фразы типа "Translation:", "Result:" и т.д.
        res = res.replace(Regex("^(?i)(translation|перевод|result|text):\\s*"), "")
        
        // 4. Убираем внешние кавычки, если ИИ обернул в них весь ответ
        if (res.startsWith("\"") && res.endsWith("\"") && res.length > 2) {
            res = res.substring(1, res.length - 1)
        }

        return res.trim()
    }

    fun needsTranslation(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.contains(Regex("[a-zA-Z]{3,}"))
    }

    suspend fun translateToEnglish(text: String, context: Context? = null): String {
        if (!text.any { it in 'а'..'я' || it in 'А'..'Я' }) return text
        val prompt = "Translate to English (botany): $text. Return ONLY translation."
        return callCascade(prompt, context) ?: text
    }

    /**
     * Умный перевод данных растения.
     * Разделяет короткие метаданные и длинные тексты для стабильности.
     */
    suspend fun translatePlantData(data: Map<String, String>, context: Context? = null): Map<String, String> {
        val result = data.toMutableMap()
        
        // 1. Короткие поля переводим пачкой (JSON)
        val metadataKeys = data.keys.filter { 
            it != "description" && it != "extract" && it != "care" && needsTranslation(data[it]) 
        }

        if (metadataKeys.isNotEmpty()) {
            val metaJson = JSONObject()
            metadataKeys.forEach { metaJson.put(it, data[it]) }
            
            val prompt = "Translate JSON values to Russian. Keep keys same. Return ONLY JSON:\n$metaJson"
            val response = callCascade(prompt, context, isJson = true)
            
            if (response != null) {
                try {
                    val translated = JSONObject(response)
                    metadataKeys.forEach { key ->
                        if (translated.has(key)) result[key] = translated.getString(key)
                    }
                } catch (e: Exception) {
                    Log.e("TranslationService", "Meta batch failed, trying fallback", e)
                }
            }
        }

        // 2. Длинные поля (описание и т.д.) переводим отдельными текстовыми запросами
        val longFields = listOf("description", "extract", "care")
        for (field in longFields) {
            val originalText = data[field]
            if (needsTranslation(originalText)) {
                val prompt = "Переведи на русский язык (ботаника). Верни ТОЛЬКО текст перевода:\n$originalText"
                val translatedText = callCascade(prompt, context, isJson = false)
                if (!translatedText.isNullOrBlank()) {
                    result[field] = translatedText
                }
            }
        }

        return result
    }

    suspend fun translateMapToRussian(data: Map<String, String>, context: Context? = null): Map<String, String> {
        return translatePlantData(data, context)
    }

    suspend fun translateToRussian(text: String, context: Context? = null): String {
        if (!needsTranslation(text)) return text
        val prompt = "Переведи на русский: $text. Верни ТОЛЬКО текст."
        return callCascade(prompt, context) ?: text
    }
}
