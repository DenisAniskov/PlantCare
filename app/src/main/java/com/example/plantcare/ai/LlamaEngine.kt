package com.example.plantcare.ai

import android.content.Context

interface LlamaEngine {
    fun isReady(): Boolean
    fun load(context: Context): Boolean
    suspend fun generate(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String
}

class LlamaEngineStub : LlamaEngine {
    private var ready = false
    override fun isReady(): Boolean = ready
    override fun load(context: Context): Boolean {
        // Stub: consider ready if model file exists
        ready = ModelManager.hasModel(context)
        return ready
    }
    override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        // Stubbed local generation; to be replaced by JNI llama.cpp
        return "[LLM-эмуляция] " + prompt.take(400)
    }
}
