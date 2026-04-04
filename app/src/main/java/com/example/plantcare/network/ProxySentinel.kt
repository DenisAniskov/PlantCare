package com.example.plantcare.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

enum class ProxyStatus {
    ACTIVE, CHECKING, UNSTABLE, OFFLINE
}

object ProxySentinel {
    private const val PROXY_URL = "https://plantcare-proxy.denis-aniskov55.workers.dev"
    private const val MAX_RETRIES = 3
    private const val CHECK_INTERVAL_MS = 60000L

    private val _status = MutableStateFlow(ProxyStatus.OFFLINE)
    val status: StateFlow<ProxyStatus> = _status.asStateFlow()

    private val _lastChecked = MutableStateFlow(0L)
    val lastChecked: StateFlow<Long> = _lastChecked.asStateFlow()

    private val _currentAttempt = MutableStateFlow(0)
    val currentAttempt: StateFlow<Int> = _currentAttempt.asStateFlow()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10000 }
        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(10, TimeUnit.SECONDS)
            }
        }
    }

    fun startMonitoring(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                checkStatus()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    suspend fun checkStatus() {
        _status.value = ProxyStatus.CHECKING
        var failures = 0

        for (i in 1..MAX_RETRIES) {
            _currentAttempt.value = i
            try {
                // Check proxy availability
                val response = client.get("$PROXY_URL/")
                if (response.status.value in 200..299) {
                    _status.value = ProxyStatus.ACTIVE
                    _currentAttempt.value = 0
                    _lastChecked.value = System.currentTimeMillis()
                    return
                } else {
                    failures++
                }
            } catch (e: Exception) {
                failures++
            }
        }

        _currentAttempt.value = failures

        when (failures) {
            MAX_RETRIES -> _status.value = ProxyStatus.OFFLINE
            in 1..<MAX_RETRIES -> _status.value = ProxyStatus.UNSTABLE
            else -> _status.value = ProxyStatus.ACTIVE
        }
        _lastChecked.value = System.currentTimeMillis()
    }
}
