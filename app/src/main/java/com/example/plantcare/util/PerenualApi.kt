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
    private const val API_KEY = "sk-x4vY69ced5ea74a8516032"
    private const val BASE_URL = "https://perenual.com/api"

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 15000 }
    }

    data class PerenualResult(
        val id: Int,
        val name: String,
        val scientificName: String = "",
        val watering: String = "",
        val sunlight: String = "",
        val careLevel: String = "",
        val description: String = "",
        val imageUrl: String? = null,
        val isDisease: Boolean = false,
        val solution: String? = null,
        val benchmark: String? = null
    )

    suspend fun searchAll(query: String, context: Context? = null, translate: Boolean = true): List<PerenualResult> = withContext(Dispatchers.IO) {
        // 1. Переводим запрос на английский, если включен перевод
        val englishQuery = if (translate) TranslationService.translateToEnglish(query, context) else query
        Log.d("PerenualApi", "Searching for: $englishQuery (orig: $query)")
        
        val plants = searchPlants(englishQuery)
        val diseases = searchDiseases(englishQuery)
        
        val rawResults = (plants + diseases).sortedBy { it.name }.take(20)
        
        if (rawResults.isEmpty()) return@withContext emptyList()

        if (translate) {
            // 2. Пакетный перевод НАЗВАНИЙ
            val nameMap = rawResults.associate { it.id.toString() + (if(it.isDisease) "_d" else "_p") to it.name }
            val translatedNames = TranslationService.translateMapToRussian(nameMap, context)

            rawResults.map { item ->
                val key = item.id.toString() + (if(item.isDisease) "_d" else "_p")
                item.copy(name = translatedNames[key] ?: item.name)
            }
        } else {
            rawResults
        }
    }

    private suspend fun searchPlants(query: String): List<PerenualResult> {
        return try {
            val response = client.get("$BASE_URL/v2/species-list") {
                parameter("key", API_KEY)
                parameter("q", query)
            }
            val json = JSONObject(response.bodyAsText())
            val data = json.optJSONArray("data") ?: return emptyList()
            val results = mutableListOf<PerenualResult>()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                results.add(PerenualResult(
                    id = item.getInt("id"),
                    name = item.optString("common_name", "Unknown"),
                    scientificName = item.optJSONArray("scientific_name")?.optString(0) ?: "",
                    imageUrl = item.optJSONObject("default_image")?.optString("thumbnail"),
                    isDisease = false
                ))
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchDiseases(query: String): List<PerenualResult> {
        return try {
            val response = client.get("$BASE_URL/pest-disease-list") {
                parameter("key", API_KEY)
                parameter("q", query)
            }
            val json = JSONObject(response.bodyAsText())
            val data = json.optJSONArray("data") ?: return emptyList()
            val results = mutableListOf<PerenualResult>()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                results.add(PerenualResult(
                    id = item.getInt("id"),
                    name = item.optString("common_name", "Disease"),
                    scientificName = item.optString("scientific_name", ""),
                    imageUrl = item.optJSONArray("images")?.optJSONObject(0)?.optString("thumbnail"),
                    isDisease = true,
                    description = item.optString("description", ""),
                    solution = item.optString("solution", "")
                ))
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getFullDetails(id: Int, isDisease: Boolean, context: Context? = null, translate: Boolean = true): PerenualResult? = withContext(Dispatchers.IO) {
        try {
            val url = if (isDisease) "$BASE_URL/pest-disease-details/$id" else "$BASE_URL/v2/species/details/$id"
            val response = client.get(url) { parameter("key", API_KEY) }
            val rawText = response.bodyAsText()
            val json = JSONObject(rawText)
            
            val benchmark = json.optJSONObject("watering_general_benchmark")?.let {
                val value = it.optString("value")
                val unit = it.optString("unit")
                "Поливать каждые $value $unit"
            }

            val scientificName = json.optJSONArray("scientific_name")?.optString(0) ?: json.optString("scientific_name", "")
            val imageUrl = json.optJSONObject("default_image")?.optString("regular_url") 
                    ?: json.optJSONArray("images")?.optJSONObject(0)?.optString("regular_url")

            if (translate) {
                // Собираем поля для пакетного перевода
                val fieldsToTranslate = mutableMapOf<String, String>()
                fieldsToTranslate["name"] = json.optString("common_name", "Unknown")
                fieldsToTranslate["description"] = json.optString("description", "")
                fieldsToTranslate["watering"] = json.optString("watering", "Regular")
                fieldsToTranslate["care_level"] = json.optString("care_level", "Medium")
                
                val sunArray = json.optJSONArray("sunlight")
                val rawSunlight = if (sunArray != null && sunArray.length() > 0) {
                    (0 until sunArray.length()).joinToString { sunArray.getString(it) }
                } else ""
                fieldsToTranslate["sunlight"] = rawSunlight
                
                if (isDisease) {
                    fieldsToTranslate["care"] = json.optString("solution", "")
                }

                val translatedFields = TranslationService.translatePlantData(fieldsToTranslate, context)

                PerenualResult(
                    id = id,
                    name = translatedFields["name"] ?: "Unknown",
                    scientificName = scientificName,
                    watering = translatedFields["watering"] ?: "Regular",
                    sunlight = translatedFields["sunlight"] ?: "",
                    careLevel = translatedFields["care_level"] ?: "Medium",
                    description = translatedFields["description"] ?: "",
                    imageUrl = imageUrl,
                    isDisease = isDisease,
                    solution = translatedFields["care"],
                    benchmark = benchmark
                )
            } else {
                val sunArray = json.optJSONArray("sunlight")
                val sunlight = if (sunArray != null && sunArray.length() > 0) {
                    (0 until sunArray.length()).joinToString { sunArray.getString(it) }
                } else ""

                PerenualResult(
                    id = id,
                    name = json.optString("common_name", "Unknown"),
                    scientificName = scientificName,
                    watering = json.optString("watering", "Regular"),
                    sunlight = sunlight,
                    careLevel = json.optString("care_level", "Medium"),
                    description = json.optString("description", ""),
                    imageUrl = imageUrl,
                    isDisease = isDisease,
                    solution = json.optString("solution", ""),
                    benchmark = benchmark
                )
            }
        } catch (e: Exception) {
            Log.e("PerenualApi", "Error fetching details: ${e.message}")
            null
        }
    }
}
