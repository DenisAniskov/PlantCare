package com.example.plantcare.ai

import android.content.Context
import com.example.plantcare.util.Prefs

object AiClientProvider {
    enum class Backend { OPENROUTER, ON_DEVICE, CASCADE }

    fun getBackend(context: Context): Backend {
        val raw = Prefs.getBackend(context, "CASCADE")
        return try {
            Backend.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            Backend.CASCADE
        }
    }

    fun setBackend(context: Context, backend: Backend) {
        Prefs.setBackend(context, backend.name)
    }

    fun get(context: Context): AiClient {
        return when (getBackend(context)) {
            Backend.OPENROUTER -> {
                val apiKey = Prefs.getOpenRouterApiKey(context)
                if (apiKey.isNotBlank()) {
                    OpenRouterAiClientWithContext(context)
                } else {
                    OnDeviceAiClient(context)
                }
            }
            Backend.ON_DEVICE -> OnDeviceAiClient(context)
            Backend.CASCADE -> CascadeAiClient(context)
        }
    }

    fun getVisionClient(context: Context): AiClient = get(context)

    fun createCascadeAiClient(context: Context): AiClient = CascadeAiClient(context)
}
