package com.example.plantcare.util

import android.content.Context
import com.example.plantcare.data.ReferencePlant
import com.example.plantcare.data.Plant
import com.example.plantcare.db.ReferencePlantDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.MapSerializer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.plantcare.data.ReferencePlantFull

object PlantJsonImporter {
    suspend fun importFromAssets(context: Context, dao: ReferencePlantDao) {
        withContext(Dispatchers.IO) {
            try {
                dao.clearAll()
                val input = context.assets.open("plants.json")
                val jsonString = input.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<ReferencePlantFull>>() {}.type
                val fullPlants: List<ReferencePlantFull> = Gson().fromJson(jsonString, type)
                val refPlants = fullPlants.map { it.toReferencePlant() }
                dao.insertAll(refPlants)
            } catch (e: Exception) {
                Log.e("PlantJsonImporter", "Ошибка импорта: ${e.message}", e)
            }
        }
    }

    fun translatePlantName(name: String?): String? {
        if (name == null) return null
        val map = mapOf(
            "Rose" to "Роза",
            "Lily" to "Лилия",
            "Sunflower" to "Подсолнух",
            "Tulip" to "Тюльпан",
            "Daisy" to "Маргаритка",
            "Orchid" to "Орхидея",
            "Carnation" to "Гвоздика",
            "Chrysanthemum" to "Хризантема",
            "Geranium" to "Герань",
            "Daffodil" to "Нарцисс",
            "Iris" to "Ирис",
            "Marigold" to "Бархатцы",
            "Sweet Pea" to "Душистый горошек",
            "Pansy" to "Анютины глазки",
            "Poppy" to "Мак",
            "Lilac" to "Сирень",
            "Cosmos" to "Космея",
            "Fuchsia" to "Фуксия",
            "Dahlia" to "Георгин",
            "Clematis" to "Клематис",
            "Heather" to "Вереск",
            "Hydrangea" to "Гортензия",
            "Begonia" to "Бегония",
            "Petunia" to "Петуния",
            "Carrot" to "Морковь",
            "Onion" to "Лук",
            "Pepper" to "Перец",
            "Tomato" to "Томат",
            "Cucumber" to "Огурец",
            "Beetroot" to "Свёкла",
            "Beet" to "Свёкла",
            "Swiss Chard" to "Мангольд",
            "Broccoli" to "Брокколи",
            "Lettuce" to "Салат",
            "Radish" to "Редис",
            "Spinach" to "Шпинат",
            "Potato" to "Картофель",
            "Pea" to "Горох",
            "Runner Bean" to "Фасоль спаржевая",
            "Brussels Sprout" to "Брюссельская капуста",
            "Rocket" to "Руккола",
            "Cauliflower" to "Цветная капуста",
            "Parsnip" to "Пастернак",
            "Leek" to "Порей",
            "Kale" to "Капуста кейл",
            "Swede" to "Брюква",
            "Broad Bean" to "Бобы",
            "Courgette" to "Кабачок",
            "Brussels Sprouts" to "Брюссельская капуста",
            "Cherry" to "Вишня",
            "Pear" to "Груша",
            "Plum" to "Слива",
            "Peach" to "Персик",
            "Grape" to "Виноград",
            "Raspberry" to "Малина",
            "Blackcurrant" to "Чёрная смородина",
            "Gooseberry" to "Крыжовник",
            "Kiwi" to "Киви",
            "Blueberry" to "Голубика",
            "Blackberry" to "Ежевика",
            "Rhubarb" to "Ревень",
            "Boxwood" to "Самшит",
            "Barberry" to "Барбарис"
            // ... и т.д.
        )
        for ((en, ru) in map) {
            if (name.contains(en, ignoreCase = true)) return ru
        }
        return name
    }

    fun translateClass(value: String?): String? = when (value?.trim()?.lowercase()) {
        "flower" -> "Цветок"
        "vegetable" -> "Овощ"
        "shrub" -> "Кустарник"
        "fruit" -> "Фрукт"
        else -> value
    }

    fun translateSoil(value: String?): String? {
        if (value == null) return null
        // Разбиваем по запятым и переводим каждую часть
        return value.split(",").joinToString(", ") { part ->
            when (part.trim().lowercase()) {
        "well-drained" -> "Хорошо дренированная почва"
        "moist and well-drained" -> "Влажная, хорошо дренированная почва"
        "moist, well-drained soil" -> "Влажная, хорошо дренированная почва"
        "moist but well-drained soil" -> "Влажная, но хорошо дренированная почва"
        "well-drained soil" -> "Хорошо дренированная почва"
        "fertile soil" -> "Плодородная почва"
        "well-drained, fertile soil" -> "Хорошо дренированная, плодородная почва"
        "acidic" -> "Кислая почва"
        "acidic, moist soil" -> "Кислая, влажная почва"
        "loamy, well-drained soil" -> "Суглинистая, хорошо дренированная почва"
        "moist" -> "Влажная почва"
                else -> part.trim()
            }
        }
    }

    fun translateMineral(value: String): String = when (value.trim().lowercase()) {
        "nitrogen" -> "Азот"
        "phosphorus" -> "Фосфор"
        "potassium" -> "Калий"
        "iron" -> "Железо"
        "magnesium" -> "Магний"
        else -> value
    }

    fun translateSeason(value: String?): String? {
        if (value == null) return null
        var result: String = value
        val replacements = mapOf(
            "spring" to "Весна",
            "summer" to "Лето",
            "autumn" to "Осень",
            "fall" to "Осень",
            "winter" to "Зима",
            "march" to "Март",
            "april" to "Апрель",
            "may" to "Май",
            "june" to "Июнь",
            "july" to "Июль",
            "august" to "Август",
            "september" to "Сентябрь",
            "october" to "Октябрь",
            "november" to "Ноябрь",
            "december" to "Декабрь",
            " or " to " или ",
            " to " to " до ",
            "," to ", ",
            "-" to "–"
        )
        for ((en, ru) in replacements) {
            result = result.replace(Regex("(?i)" + Regex.escape(en)), ru)
        }
        return result
    }

    fun translateWatering(value: Int?): String? = when (value) {
        1 -> "Редкий"
        2 -> "Умеренный"
        3 -> "Частый"
        else -> null
    }

    fun translateLight(value: String?): String? {
        if (value == null) return null
        // Разбиваем по запятым и переводим каждую часть
        return value.split(",").joinToString(", ") { part ->
            when (part.trim().lowercase()) {
        "full sunlight" -> "Полное солнце"
        "full sun" -> "Полное солнце"
        "partial shade" -> "Полутень"
        "filtered sunlight" -> "Фильтрованный свет"
        "partial sun" -> "Полутень"
        "partial shade to full sun" -> "От полутени до полного солнца"
        "full sun to partial shade" -> "От полного солнца до полутени"
        "full sunlight to partial shade" -> "От полного солнца до полутени"
        "shade or partial shade" -> "Тень или полутень"
                else -> part.trim()
    }
        }
    }

    fun translateToxicity(value: String?): String? = when (value?.trim()?.lowercase()) {
        "toxic to animals and humans if ingested in large amounts" -> "Токсично для животных и людей при употреблении в больших количествах"
        "toxic to animals and humans if ingested" -> "Токсично для животных и людей при употреблении"
        "toxic to animals if ingested in large amounts" -> "Токсично для животных при употреблении в больших количествах"
        "toxic to animals if ingested" -> "Токсично для животных при употреблении"
        "toxic to humans if ingested in large amounts" -> "Токсично для людей при употреблении в больших количествах"
        "toxic to humans if ingested" -> "Токсично для людей при употреблении"
        "non-toxic to humans and animals" -> "Нетоксично для людей и животных"
        "toxic to animals" -> "Токсично для животных"
        "toxic to humans" -> "Токсично для людей"
        "toxic to animals and humans" -> "Токсично для животных и людей"
        "non-toxic" -> "Нетоксично"
        "leaves and stems are toxic to animals" -> "Листья и стебли токсичны для животных"
        "seeds and pods are toxic" -> "Семена и стручки токсичны"
        "sap can cause skin irritation" -> "Сок может вызвать раздражение кожи"
        "toxic to humans and animals when raw" -> "Токсично для людей и животных в сыром виде"
        "non-toxic to animals and humans" -> "Нетоксично для животных и людей"
        "toxic to dogs" -> "Токсично для собак"
        "toxic to dogs and cats" -> "Токсично для собак и кошек"
        else -> value
    }

    private fun parseToxicity(value: String?): Pair<Boolean?, String?> {
        val str = value?.trim()?.lowercase() ?: return null to null
        return when {
            str in listOf(
                "true", "toxic", "yes", "1",
                "toxic to animals and humans if ingested in large amounts",
                "toxic to animals",
                "toxic to humans if ingested in large amounts",
                "toxic to animals if ingested in large amounts",
                "toxic to humans if ingested",
                "toxic to animals if ingested",
                "toxic to animals and humans if ingested",
                "toxic to humans",
                "toxic to animals and humans",
                "leaves and stems are toxic to animals",
                "seeds and pods are toxic",
                "sap can cause skin irritation",
                "toxic to humans and animals when raw",
                "toxic to dogs",
                "toxic to dogs and cats"
            ) -> true to null
            str in listOf(
                "false", "non-toxic", "no", "0",
                "non-toxic to animals and humans"
            ) -> false to null
            str.isNotBlank() -> null to value
            else -> null to null
        }
    }

    private fun buildDescription(plant: ReferencePlant, toxicityRaw: String?): String {
        val sb = StringBuilder()
        plant.name?.let { sb.append("Название: $it\n") }
        plant.description?.let { sb.append("Описание: $it\n") }
        plant.imageRes?.let { sb.append("Изображение: $it\n") }
        plant.watering?.let { sb.append("Полив: $it\n") }
        plant.light?.let { sb.append("Освещение: $it\n") }
        plant.temperature?.let { sb.append("Температура: $it\n") }
        plant.fertilizing?.let { sb.append("Удобрение: $it\n") }
        plant.notes?.let { sb.append("Примечания: $it\n") }
        plant.toxicityNote?.let { sb.append("Токсичность: $it\n") }
        return sb.toString().trim()
    }

    private fun toReferencePlant(it: ReferencePlant): ReferencePlant {
        return it
    }
}

fun ReferencePlantFull.toReferencePlant(): ReferencePlant = ReferencePlant(
    id = 0, // Room autogen
    name = name,
    description = "Почва: $soil\nВлажность: $humidity\nТемпература: $temperature\nРазмножение: ${propagation.joinToString(", ")}\nОсобенности: $difficulties",
            imageRes = null,
    watering = watering,
    light = light,
    temperature = temperature,
    fertilizing = fertilizer,
            notes = null,
    toxicityNote = null
) 