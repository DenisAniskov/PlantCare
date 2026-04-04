package com.example.plantcare.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.plantcare.core.LocalRagEngine
import java.io.BufferedReader

/**
 * Offline baseline using local knowledge base (RAG).
 * Generates structured Russian answer from local knowledge base.
 */
class OnDeviceAiClient(private val context: Context) : AiClient {
    private fun readAsset(name: String): String = try {
        context.assets.open(name).bufferedReader().use(BufferedReader::readText)
    } catch (_: Exception) { "[]" }

    private val rag by lazy {
        LocalRagEngine(
            plantsJson = readAsset("plants.json"),
            pestsJson = readAsset("pests.json"),
            diseasesJson = readAsset("diseases.json"),
            tipsJson = readAsset("plant_care_tips.json")
        )
    }
    // Optional: enabled only when the native JNI library is present.
    private val llama: LlamaEngineNative? by lazy {
        val native = LlamaEngineNative()
        if (native.load(context)) native else null
    }

    override suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?
    ): Result<String> = withContext(Dispatchers.Default) {
        val query = userText?.takeIf { it.isNotBlank() } ?: "вопрос про уход за растениями"

        // Спец‑случай: короткое приветствие — отвечаем одной уточняющей фразой без приветствий
        if (isGreeting(query)) {
            return@withContext Result.success("Какое у вас растение и что хотите узнать об уходе?")
        }
        // Режим "Справочник": сначала пробуем точечный автопоиск
        rag.fetchEntry(query)?.let { (title, text) ->
            val out = buildString {
                appendLine("[Справочник: автоматический поиск]")
                appendLine("Название: $title")
                append(text)
            }.trim()
            return@withContext Result.success(out)
        }

        // Если не нашли точное совпадение — попробуем поиск по базе знаний
        val hits = rag.search(query, limit = 5)
        if (hits.isNotEmpty()) {
            val out = buildString {
                appendLine("[Справочник: результаты поиска]")
                hits.forEachIndexed { idx, h ->
                    appendLine()
                    appendLine("${idx + 1}) ${h.title}")
                    appendLine(h.text)
                }
            }.trim()
            return@withContext Result.success(out)
        }

        // Если не нашли — сообщаем об отсутствии
        Result.success("[Справочник] Нет записи для: \"$query\". Попробуйте другое ключевое слово (например: \"мучнистая роса\", \"фикус\", \"тля\").")
    }

    override suspend fun sendMessageStreaming(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        onChunk: (String) -> Unit
    ): Result<String> = sendMessage(history, userText, imageBase64, systemPrompt)

    private fun sanitize(text: String): String {
        var t = text
        // Удалим возможные эхо-метки из промпта
        t = t.replace(Regex("^\\s*Контекст:.*", RegexOption.MULTILINE), "")
        t = t.replace(Regex("^\\s*Вопрос пользователя:.*", RegexOption.MULTILINE), "")
        // Уберем строки-инструкции/мета
        t = t.replace(Regex("(?i)^\\s*(деляй|разделяй|ответь|вставь|привет|здравствуйте|здарова|здравствуй).*" , RegexOption.MULTILINE), "")
        // Уберем префиксы вроде "Ответчик:", "Assistant:", "Ответ:", "Answer:"
        t = t.replace(Regex("(?i)^(\\s*)(ответчик|assistant|ответ|answer)\\s*:\\s*", RegexOption.MULTILINE), "$1")
        // Подрежем ведущие/лишние пустые строки
        t = t.lines().filter { it.isNotBlank() }.joinToString("\n")
        return t.trim()
    }

    private fun isEmulator(): Boolean {
        return (
            android.os.Build.FINGERPRINT?.contains("generic", ignoreCase = true) == true ||
            android.os.Build.FINGERPRINT?.contains("ranchu", ignoreCase = true) == true ||
            android.os.Build.MODEL?.contains("google_sdk", ignoreCase = true) == true ||
            android.os.Build.MODEL?.contains("Emulator", ignoreCase = true) == true ||
            android.os.Build.MANUFACTURER?.contains("Genymotion", ignoreCase = true) == true ||
            android.os.Build.BRAND?.startsWith("generic") == true ||
            android.os.Build.PRODUCT?.contains("sdk", ignoreCase = true) == true
        )
    }

    private fun isGreeting(q: String): Boolean {
        val s = q.trim().lowercase()
        if (s.length > 20) return false
        val words = listOf("привет", "здравствуйте", "здравствуй", "хай", "салют", "йо", "hello", "hi", "hey")
        return words.any { w -> s == w || s.startsWith(w) }
    }
}
