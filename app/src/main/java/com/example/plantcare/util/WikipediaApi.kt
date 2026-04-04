package com.example.plantcare.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Wikipedia (рус.): поиск статей, заголовок, текст.
 */
data class WikipediaSummary(
    val title: String,
    val extract: String,
    val thumbnailUrl: String? = null
)

object WikipediaApi {
    private const val BASE = "https://ru.wikipedia.org"
    private const val USER_AGENT = "PlantCare/1.0 (Android; plants reference)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
            )
        }
        .build()

    suspend fun searchAndSummary(query: String): Result<WikipediaSummary> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext Result.failure(Exception("Пустой запрос"))
        try {
            val title = searchTitle(q) ?: return@withContext Result.failure(Exception("Ничего не найдено"))
            fetchSummary(title)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchTitle(query: String): String? {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "$BASE/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val queryObj = json.optJSONObject("query") ?: return null
            val search = queryObj.optJSONArray("search") ?: return null
            if (search.length() == 0) return null
            val first = search.getJSONObject(0)
            return first.optString("title", "").takeIf { it.isNotBlank() }
        }
    }

    private fun fetchSummary(title: String): Result<WikipediaSummary> {
        val encoded = URLEncoder.encode(title, Charsets.UTF_8.name()).replace("+", "%20")
        val url = "$BASE/api/rest_v1/page/summary/$encoded"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
            val body = response.body?.string() ?: return Result.failure(Exception("Пустой ответ"))
            val json = JSONObject(body)
            val type = json.optString("type", "")
            if (type == "disambiguation") {
                val extract = json.optString("extract", "Несколько статей с таким названием. Уточните запрос.")
                return Result.success(
                    WikipediaSummary(
                        title = json.optString("title", title),
                        extract = extract,
                        thumbnailUrl = json.optJSONObject("thumbnail")?.optString("source")
                    )
                )
            }
            val extract = json.optString("extract", "").trim()
            if (extract.isBlank()) return Result.failure(Exception("Нет текста статьи"))
            val thumb = json.optJSONObject("thumbnail")?.optString("source")
            return Result.success(
                WikipediaSummary(
                    title = json.optString("title", title),
                    extract = extract,
                    thumbnailUrl = thumb
                )
            )
        }
    }
}
