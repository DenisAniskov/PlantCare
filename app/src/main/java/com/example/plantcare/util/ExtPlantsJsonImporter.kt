package com.example.plantcare.util

import android.content.Context
import android.util.Log
import com.example.plantcare.data.ReferencePlant
import com.example.plantcare.db.ReferencePlantDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Дополнительный источник растений из ext_plants.json.
 * Формат: { "plants": [ { "common_name_ru", "scientific_name", "water", "light", ... } ] }
 */
object ExtPlantsJsonImporter {
    suspend fun importFromAssets(context: Context, dao: ReferencePlantDao) = withContext(Dispatchers.IO) {
        try {
            val input = context.assets.open("ext_plants.json")
            val jsonString = input.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val plantsArray = json.optJSONArray("plants") ?: return@withContext
            val list = mutableListOf<ReferencePlant>()
            for (i in 0 until plantsArray.length()) {
                val obj = plantsArray.getJSONObject(i)
                val plant = parseToReferencePlant(obj) ?: continue
                list.add(plant)
            }
            if (list.isNotEmpty()) dao.insertAll(list)
        } catch (e: Exception) {
            Log.e("ExtPlantsImporter", "Ошибка импорта ext_plants: ${e.message}", e)
        }
    }

    private fun parseToReferencePlant(obj: JSONObject): ReferencePlant? {
        val name = obj.optString("common_name_ru", "").takeIf { it.isNotBlank() } ?: return null
        val scientific = obj.optString("scientific_name", "")
        val water = obj.optString("water", "")
        val light = obj.optString("light", "")
        val soil = obj.optString("soil", "")
        val humidity = obj.optString("humidity", "")
        val tempObj = obj.optJSONObject("temperature_c")
        val temperature = when {
            tempObj != null -> {
                val min = tempObj.optInt("min", 0)
                val max = tempObj.optInt("max", 0)
                if (min != 0 || max != 0) "$min–$max°C" else ""
            }
            else -> ""
        }
        val toxicity = obj.optString("toxicity", "")
        val bloom = obj.optString("bloom", "")
        val origin = obj.optString("origin", "")
        val notes = obj.optString("notes", "")
        val type = obj.optString("type", "")
        val family = obj.optString("family", "")
        val lifecycle = obj.optString("lifecycle", "")

        val description = buildString {
            if (scientific.isNotBlank()) appendLine("Научное название: $scientific")
            if (family.isNotBlank()) appendLine("Семейство: $family")
            if (type.isNotBlank()) appendLine("Тип: $type")
            if (lifecycle.isNotBlank()) appendLine("Жизненный цикл: $lifecycle")
            if (soil.isNotBlank()) appendLine("Почва: $soil")
            if (humidity.isNotBlank()) appendLine("Влажность: $humidity")
            if (temperature.isNotBlank()) appendLine("Температура: $temperature")
            if (bloom.isNotBlank()) appendLine("Цветение: $bloom")
            if (origin.isNotBlank()) append("Происхождение: $origin")
        }.trim()

        return ReferencePlant(
            id = 0,
            name = name,
            description = description.ifBlank { "Дополнительная информация в справочнике." },
            imageRes = null,
            watering = water.ifBlank { null },
            light = light.ifBlank { null },
            temperature = temperature.ifBlank { null },
            fertilizing = null,
            notes = notes.ifBlank { null },
            toxicityNote = toxicity.ifBlank { null },
            isFavorite = false
        )
    }
}
