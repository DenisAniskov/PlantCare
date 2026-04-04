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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TranslationService {
    private const val DEFAULT_API_KEY = "YOUR_OPENROUTER_API_KEY"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun translateToRussian(text: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        if (text.isBlank() || text.length < 3) return@withContext text
        
        val apiKey = try {
            context?.let { Prefs.getOpenRouterApiKey(it) } ?: DEFAULT_API_KEY
        } catch (e: Exception) {
            DEFAULT_API_KEY
        }
        
        try {
            val prompt = "Переведи на русский язык. Верни только перевод, без комментариев:\n$text"
            
            val body = JSONObject(mapOf(
                "model" to "meta-llama/llama-3.1-8b-instruct:free",
                "messages" to arrayOf(
                    JSONObject(mapOf("role" to "user", "content" to prompt))
                ),
                "max_tokens" to 500
            )).toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (responseBody != null && response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    if (json.has("choices")) {
                        val translated = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                        Log.d("TranslationService", "Translated: $text -> $translated")
                        return@withContext translated
                    }
                }
                Log.w("TranslationService", "Translation failed, returning original")
                text
            }
        } catch (e: Exception) {
            Log.e("TranslationService", "Translation error: ${e.message}")
            text
        }
    }
}
