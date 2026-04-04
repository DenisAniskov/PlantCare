package com.example.plantcare.data

data class ReferencePlantFull(
    val id: Int,
    val name: String,
    val light: String,
    val watering: String,
    val temperature: String,
    val humidity: String,
    val soil: String,
    val fertilizer: String,
    val propagation: List<String>,
    val difficulties: String,
    val diseases: List<Int>
) 