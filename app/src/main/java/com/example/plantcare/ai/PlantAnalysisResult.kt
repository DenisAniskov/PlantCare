package com.example.plantcare.ai

import com.google.gson.annotations.SerializedName

data class PlantAnalysisResult(
    val name: String = "",
    val latinName: String = "",
    val healthStatus: String = "",
    val healthScore: Int = 50,
    val problems: List<String> = emptyList(),
    val treatment: List<String> = emptyList(),
    @SerializedName("careInstructions") val care: CareInstructionsResult = CareInstructionsResult(),
    val facts: List<String> = emptyList()
)

data class CareInstructionsResult(
    val watering: String = "",
    val light: String = "",
    val temperature: String = "",
    val fertilizer: String = ""
)
