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
        val backend = getBackend(context)
        return when (backend) {
            Backend.OPENROUTER -> OpenRouterAiClientWithContext(context)
            Backend.ON_DEVICE -> OnDeviceAiClient(context)
            Backend.CASCADE -> CascadeAiClient(context)
        }
    }

    fun getVisionClient(context: Context): AiClient = get(context)

    fun createCascadeAiClient(context: Context): AiClient = CascadeAiClient(context)
}
