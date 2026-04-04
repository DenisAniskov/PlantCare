package com.example.plantcare.util

import android.content.Context
import android.widget.ImageView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ProxyStatusMonitor {
    private const val PROXY_CHECK_URL = "https://plantcare-proxy.denis-aniskov55.workers.dev/"
    private const val CHECK_INTERVAL_MS = 60000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var monitorJob: Job? = null
    private var currentStatus: Status = Status.UNKNOWN

    enum class Status {
        LIVE, OFFLINE, UNKNOWN
    }

    data class StatusInfo(
        val status: Status,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun startMonitoring(context: Context, indicator: ImageView?, onStatusChange: ((Status) -> Unit)? = null) {
        stopMonitoring()
        
        monitorJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val statusInfo = checkProxyStatus(context)
                currentStatus = statusInfo.status
                
                withContext(Dispatchers.Main) {
                    indicator?.let { updateIndicator(it, statusInfo.status) }
                    onStatusChange?.invoke(statusInfo.status)
                }
                
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    suspend fun checkProxyStatus(context: Context): StatusInfo = withContext(Dispatchers.IO) {
        val proxyUrl = Prefs.getProxyBaseUrl(context)
        val checkUrl = if (proxyUrl.endsWith("/")) proxyUrl.dropLast(1) else proxyUrl

        try {
            val request = Request.Builder()
                .url(checkUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.contains("Proxy is LIVE", ignoreCase = true)) {
                StatusInfo(Status.LIVE, "Прокси работает")
            } else {
                StatusInfo(Status.OFFLINE, "Прокси недоступен: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            StatusInfo(Status.OFFLINE, "Ошибка: ${e.message ?: "неизвестная ошибка"}")
        }
    }

    private fun updateIndicator(view: ImageView, status: Status) {
        val colorRes = when (status) {
            Status.LIVE -> android.R.color.holo_green_dark
            Status.OFFLINE -> android.R.color.holo_red_dark
            Status.UNKNOWN -> android.R.color.darker_gray
        }
        view.setColorFilter(view.context.getColor(colorRes))
    }

    fun showStatusToast(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            val statusInfo = checkProxyStatus(context)
            val message = when (statusInfo.status) {
                Status.LIVE -> "🟢 Прокси работает"
                Status.OFFLINE -> "🔴 Прокси недоступен\n${statusInfo.message}"
                Status.UNKNOWN -> "⚪ Статус неизвестен"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun getCurrentStatus(): Status = currentStatus
}