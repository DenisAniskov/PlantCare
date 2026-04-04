package com.example.plantcare.core

import kotlin.math.min
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Plant(
    val id: Int? = null,
    val name: String = "",
    val light: String = "",
    val watering: String = "",
    val temperature: String = "",
    val humidity: String = "",
    val soil: String = "",
    val fertilizer: String = "",
    val propagation: List<String> = emptyList(),
    val difficulties: String = "",
)

@Serializable
data class Disease(
    val id: Int? = null,
    val name: String = "",
    val symptoms: List<String> = emptyList(),
    val treatment: List<String> = emptyList(),
    val causes: List<String> = emptyList(),
    val prevention: String = "",
    val affected_plants: List<String> = emptyList(),
)

@Serializable
data class Pest(
    val id: Int? = null,
    val name: String = "",
    val symptoms: List<String> = emptyList(),
    val causes: List<String> = emptyList(),
    val treatment: List<String> = emptyList(),
    val prevention: String = "",
)

@Serializable
data class Tip(
    val plant: String = "",
    @SerialName("care_tip") val careTip: String = "",
)

class LocalRagEngine(
    private val plantsJson: String,
    private val pestsJson: String,
    private val diseasesJson: String,
    private val tipsJson: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val plants: List<Plant> = runCatching { json.decodeFromString<List<Plant>>(plantsJson) }.getOrDefault(emptyList())
    private val pests: List<Pest> = runCatching { json.decodeFromString<List<Pest>>(pestsJson) }.getOrDefault(emptyList())
    private val diseases: List<Disease> = runCatching { json.decodeFromString<List<Disease>>(diseasesJson) }.getOrDefault(emptyList())
    private val tips: List<Tip> = runCatching { json.decodeFromString<List<Tip>>(tipsJson) }.getOrDefault(emptyList())

    private val plantsMap by lazy { buildPlantsMap() }
    private val pestsMap by lazy { buildPestsMap() }
    private val diseasesMap by lazy { buildDiseasesMap() }
    private val tipsMap by lazy { buildTipsMap() }

    data class Snippet(val title: String, val text: String, val score: Int)

    /** Строгий поиск только по болезням и вредителям — для диагностики. Требует минимум 2 совпадений. */
    fun searchDiseases(query: String, limit: Int = 5): List<Snippet> {
        val q = query.lowercase()
        val tokens = extractMeaningfulTokens(q)
        if (tokens.isEmpty()) return emptyList()
        val results = mutableListOf<Snippet>()
        for (d in diseases) {
            val name = d.name
            val symptoms = d.symptoms.joinToString("; ")
            val treatment = d.treatment.joinToString("; ")
            val causes = d.causes.joinToString("; ")
            val prevention = d.prevention
            val affected = d.affected_plants.joinToString("; ")
            val searchText = "$name $symptoms $treatment $causes $prevention $affected".lowercase()
            val score = tokens.count { searchText.contains(it) }
            if (score >= 2) results += Snippet(name, buildString {
                if (symptoms.isNotBlank()) appendLine("Симптомы: $symptoms")
                if (treatment.isNotBlank()) append("Лечение: $treatment")
            }.trim(), score)
        }
        for (p in pests) {
            val name = p.name
            val symptoms = p.symptoms.joinToString("; ")
            val treatment = p.treatment.joinToString("; ")
            val causes = p.causes.joinToString("; ")
            val prevention = p.prevention
            val searchText = "$name $symptoms $treatment $causes $prevention".lowercase()
            val score = tokens.count { searchText.contains(it) }
            if (score >= 2) results += Snippet(name, buildString {
                if (symptoms.isNotBlank()) appendLine("Симптомы: $symptoms")
                if (treatment.isNotBlank()) append("Борьба: $treatment")
            }.trim(), score)
        }
        return results.sortedByDescending { it.score }.take(limit)
    }

    private val stopwords = setOf(
        "на", "в", "у", "и", "по", "что", "как", "при", "из", "с", "со", "к", "от", "для", "за", "не", "но",
        "это", "или", "есть", "было", "есть", "будет", "место", "растение", "симптомы"
    )

    private fun extractMeaningfulTokens(query: String): List<String> = query
        .lowercase()
        .replace('ё', 'е')
        .split(Regex("[\\s,.;:!?]+"))
        .map { it.filter { c -> c.isLetter() } }
        .filter { it.length >= 2 && it !in stopwords }

    fun search(query: String, limit: Int = 5): List<Snippet> {
        val q = query.lowercase()
        val results = mutableListOf<Snippet>()
        for (obj in tips) {
            val title = obj.plant
            val text = obj.careTip
            val score = scoreText(q, "$title\n$text")
            if (score > 0) results += Snippet(title, text, score)
        }
        for (d in diseases) {
            val name = d.name
            val symptoms = d.symptoms.joinToString("; ")
            val treatment = d.treatment.joinToString("; ")
            val text = buildString {
                if (symptoms.isNotBlank()) appendLine("Симптомы: $symptoms")
                if (treatment.isNotBlank()) append("Лечение: $treatment")
            }.trim()
            val score = scoreText(q, "$name\n$text")
            if (score > 0) results += Snippet(name, text, score)
        }
        return results.sortedByDescending { it.score }.take(limit)
    }

    fun fetchEntry(query: String): Pair<String, String>? {
        val qRaw = query.trim()
        if (qRaw.isBlank()) return null
        val qTokens = qRaw.split(" ", "\t", ",", ".", "?", "!").map { stem(norm(it)) }.filter { it.length >= 3 }
        if (qTokens.isEmpty()) return null
        val allMaps: List<Map<String, Pair<String, String>>> = listOf(plantsMap, tipsMap, pestsMap, diseasesMap)
        for (m in allMaps) for ((k, v) in m) if (qTokens.any { it == stem(norm(k)) }) return v
        var best: Pair<String, Pair<String, String>>? = null
        var bestScore = Int.MAX_VALUE
        for (m in allMaps) {
            for ((k, v) in m) {
                val nk = stem(norm(k))
                for (qt in qTokens) {
                    val d = levenshtein(nk, qt)
                    val thr = when {
                        nk.length <= 6 -> 2
                        nk.length <= 10 -> 3
                        else -> 4
                    }
                    if (d <= thr && d < bestScore) {
                        best = k to v
                        bestScore = d
                        if (bestScore == 0) break
                    }
                }
            }
        }
        return best?.second
    }

    private fun buildTipsMap(): Map<String, Pair<String, String>> = buildMap {
        for (t in tips) {
            val title = t.plant
            val text = t.careTip
            if (title.isNotBlank()) put(title.lowercase(), title to text)
        }
    }

    private fun buildDiseasesMap(): Map<String, Pair<String, String>> = buildMap {
        for (d in diseases) {
            val name = d.name
            if (name.isBlank()) continue
            val symptoms = d.symptoms.joinToString("; ")
            val treatment = d.treatment.joinToString("; ")
            val text = buildString {
                if (symptoms.isNotBlank()) appendLine("Симптомы: $symptoms")
                if (treatment.isNotBlank()) append("Лечение: $treatment")
            }.trim()
            put(name.lowercase(), name to text)
        }
    }

    private fun buildPestsMap(): Map<String, Pair<String, String>> = buildMap {
        for (p in pests) {
            val name = p.name
            if (name.isBlank()) continue
            val symptoms = p.symptoms.joinToString("; ")
            val causes = p.causes.joinToString("; ")
            val treatment = p.treatment.joinToString("; ")
            val prevention = p.prevention
            val text = buildString {
                if (symptoms.isNotBlank()) appendLine("Симптомы: $symptoms")
                if (causes.isNotBlank()) appendLine("Причины: $causes")
                if (treatment.isNotBlank()) appendLine("Борьба: $treatment")
                if (prevention.isNotBlank()) append("Профилактика: $prevention")
            }.trim()
            put(name.lowercase(), name to text)
        }
    }

    private fun buildPlantsMap(): Map<String, Pair<String, String>> = buildMap {
        for (p in plants) {
            val name = p.name
            if (name.isBlank()) continue
            val propagation = p.propagation.joinToString(", ")
            val text = buildString {
                if (p.light.isNotBlank()) appendLine("Свет: ${p.light}")
                if (p.watering.isNotBlank()) appendLine("Полив: ${p.watering}")
                if (p.temperature.isNotBlank()) appendLine("Температура: ${p.temperature}")
                if (p.humidity.isNotBlank()) appendLine("Влажность: ${p.humidity}")
                if (p.soil.isNotBlank()) appendLine("Почва: ${p.soil}")
                if (p.fertilizer.isNotBlank()) appendLine("Подкормка: ${p.fertilizer}")
                if (propagation.isNotBlank()) appendLine("Размножение: $propagation")
                if (p.difficulties.isNotBlank()) append("Особенности: ${p.difficulties}")
            }.trim()
            put(name.lowercase(), name to text)
        }
    }

    private fun scoreText(query: String, text: String): Int {
        var score = 0
        val t = text.lowercase()
        query.split(" ", ",", ".").filter { it.isNotBlank() }.forEach { token ->
            if (t.contains(token)) score += 1
        }
        return score
    }

    private fun norm(s: String): String {
        val lower = s.lowercase().replace('ё', 'е')
        val sb = StringBuilder(lower.length)
        for (ch in lower) if (ch.isLetter()) sb.append(ch)
        return sb.toString()
    }

    private fun stem(s: String): String {
        var t = s
        val endings = listOf(
            "иями","ями","ами","ями","ыми","его","ого","ему","ому","ее","ие","ые","ой","ий","ый","ая","яя","ое","ее",
            "ам","ям","ом","ем","ах","ях","ов","ев","ую","ью","ия","ья","ю","е","а","я","ы","и","ой","ёй"
        )
        for (e in endings) {
            if (t.length > e.length + 3 && t.endsWith(e)) return t.dropLast(e.length)
        }
        return t
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val m = b.length + 1
        val dp = IntArray(m) { it }
        for (i in 1..a.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + cost
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
