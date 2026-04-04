package com.example.plantcare.ai

object LlamaNativeLoader {
    val loaded: Boolean = try {
        System.loadLibrary("llama_engine")
        true
    } catch (_: Throwable) {
        false
    }
}

class LlamaEngineNative : LlamaEngine {
    private var ready = false
    override fun isReady(): Boolean = ready
    override fun load(context: android.content.Context): Boolean {
        ready = LlamaNativeLoader.loaded && ModelManager.hasModel(context)
        if (ready) {
            val ok = initModel(ModelManager.getModelFile(context).absolutePath)
            ready = ready && ok
        }
        return ready
    }
    external fun initModel(modelPath: String): Boolean
    external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        if (!ready) return ""
        return nativeGenerate(prompt, maxTokens, temperature)
    }
}
