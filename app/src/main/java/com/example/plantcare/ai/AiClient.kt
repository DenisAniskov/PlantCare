package com.example.plantcare.ai

interface AiClient {
    /**
     * Sends a chat message to the AI backend.
     * @param history The prior messages in OpenAI-compatible format (role/content).
     * @param userText Optional user text.
     * @param imageBase64 Optional base64 JPEG image without data: prefix.
     * @param systemPrompt Optional system instruction.
     * @return Result with assistant text response.
     */
    suspend fun sendMessage(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String? = null
    ): Result<String>

    suspend fun sendMessageStreaming(
        history: List<Map<String, Any>>,
        userText: String?,
        imageBase64: String?,
        systemPrompt: String?,
        onChunk: (String) -> Unit
    ): Result<String>

    fun setStatusCallback(callback: (String) -> Unit) {
        // Default no-op implementation
    }
}
