package com.example.plantcare.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class AiRequest(
    val prompt: String,
    val stream: Boolean = false,
    val model: String? = null,
    val imageBase64: String? = null,
    val systemPrompt: String? = null,
    val history: List<Map<String, String>>? = null
)

@Serializable
data class AiResponse(
    val content: String? = null,
    val error: String? = null
)

@Serializable
data class ChatChunk(
    val text: String? = null,
    val isFinal: Boolean = false
)
