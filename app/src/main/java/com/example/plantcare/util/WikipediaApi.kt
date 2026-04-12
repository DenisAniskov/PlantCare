package com.example.plantcare.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Wikipedia search and summary API.
 */
data class WikipediaSummary(
    val title: String,
    val extract: String,
    val thumbnailUrl: String? = null
)

object WikipediaApi {
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

    private fun getBase(lang: String) = "https://$lang.wikipedia.org"

    suspend fun searchAndSummary(query: String): Result<WikipediaSummary> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext Result.failure(Exception("Empty query"))
        
        // Decide language: if mostly ASCII, try English first, then Russian. Otherwise vice-versa.
        val isEnglish = q.all { it.code < 128 }
        val langs = if (isEnglish) listOf("en", "ru") else listOf("ru", "en")
        
        var lastError: Exception? = null
        for (lang in langs) {
            try {
                val title = searchTitle(q, lang)
                if (title != null) {
                    val summary = fetchSummary(title, lang)
                    if (summary.isSuccess) return@withContext summary
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        
        Result.failure(lastError ?: Exception("Nothing found for '$query'"))
    }

    private fun searchTitle(query: String, lang: String): String? {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "${getBase(lang)}/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1"
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

    private fun fetchSummary(title: String, lang: String): Result<WikipediaSummary> {
        val encoded = URLEncoder.encode(title, Charsets.UTF_8.name()).replace("+", "%20")
        val url = "${getBase(lang)}/api/rest_v1/page/summary/$encoded"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = JSONObject(body)
            val type = json.optString("type", "")
            if (type == "disambiguation") {
                val extract = json.optString("extract", "Multiple articles found. Please clarify.")
                return Result.success(
                    WikipediaSummary(
                        title = json.optString("title", title),
                        extract = extract,
                        thumbnailUrl = json.optJSONObject("thumbnail")?.optString("source")
                    )
                )
            }
            val extract = json.optString("extract", "").trim()
            if (extract.isBlank()) return Result.failure(Exception("No extract"))
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
