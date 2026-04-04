package com.example.plantcare.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.URLEncoder

object PixabayApi {
    private const val API_KEY = "YOUR_PIXABAY_API_KEY"
    private const val BASE_URL = "https://pixabay.com/api"
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 15000 }
        engine {
            config {
                followRedirects(true)
            }
        }
    }

    data class ImageResult(
        val previewUrl: String,
        val webUrl: String,
        val tags: String
    )

    suspend fun searchPlantImage(query: String): ImageResult? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode("$query plant", "UTF-8")
            val response = client.get(BASE_URL) {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                header("Referer", "https://pixabay.com/")
                parameter("key", API_KEY)
                parameter("q", encoded)
                parameter("image_type", "photo")
                parameter("per_page", 3)
                parameter("safesearch", true)
            }
            if (response.status.value !in 200..299) {
                Log.e("PixabayApi", "HTTP ${response.status}")
                return@withContext null
            }
            val body = response.bodyAsText()
            val json = JSONObject(body)
            val hits = json.optJSONArray("hits")
            if (hits == null || hits.length() == 0) return@withContext null
            val first = hits.getJSONObject(0)
            ImageResult(
                previewUrl = first.optString("previewURL", ""),
                webUrl = first.optString("webformatURL", ""),
                tags = first.optString("tags", "")
            )
        } catch (e: Exception) {
            Log.e("PixabayApi", "Search error: ${e.message}", e)
            null
        }
    }
}
