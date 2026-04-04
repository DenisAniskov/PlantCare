package com.example.plantcare.util

import android.content.Context
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
import com.example.plantcare.ai.TranslationService

object PerenualApi {
    private const val API_KEY = "YOUR_PERENUAL_API_KEY"
    private const val BASE_URL = "https://perenual.com/api"
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 15000 }
    }

    data class PlantCareInfo(
        val name: String,
        val scientificName: String,
        val watering: String,
        val sunlight: String,
        val careLevel: String,
        val description: String
    )

    suspend fun searchPlants(query: String, context: Context? = null): List<PlantCareInfo> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$BASE_URL/species-list") {
                parameter("key", API_KEY)
                parameter("q", query)
            }
            if (response.status.value !in 200..299) {
                Log.e("PerenualApi", "HTTP ${response.status}")
                return@withContext emptyList()
            }
            val body = response.bodyAsText()
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val results = mutableListOf<PlantCareInfo>()
            for (i in 0 until minOf(data.length(), 10)) {
                val item = data.getJSONObject(i)
                results.add(
                    PlantCareInfo(
                        name = item.optString("common_name", ""),
                        scientificName = item.optString("scientific_name", ""),
                        watering = "",
                        sunlight = "",
                        careLevel = "",
                        description = ""
                    )
                )
            }
            results
        } catch (e: Exception) {
            Log.e("PerenualApi", "Search error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getPlantDetails(speciesId: Int, context: Context? = null): PlantCareInfo? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$BASE_URL/species/$speciesId") {
                parameter("key", API_KEY)
            }
            if (response.status.value !in 200..299) return@withContext null
            val body = response.bodyAsText()
            val json = JSONObject(body)
            val care = json.optJSONObject("care_level")
            val watering = care?.optString("general", "") ?: ""
            val sunlight = json.optString("sunlight") ?: ""
            val careLevel = care?.optString("general", "") ?: ""
            val description = json.optString("description", "")
            
            val translatedDesc = if (description.isNotBlank() && context != null) {
                try {
                    TranslationService.translateToRussian(description, context)
                } catch (e: Exception) {
                    description
                }
            } else description
            
            PlantCareInfo(
                name = json.optString("common_name", ""),
                scientificName = json.optString("scientific_name", ""),
                watering = watering,
                sunlight = sunlight,
                careLevel = careLevel,
                description = translatedDesc
            )
        } catch (e: Exception) {
            Log.e("PerenualApi", "Details error: ${e.message}", e)
            null
        }
    }
}
