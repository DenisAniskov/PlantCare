package com.example.plantcare.data

data class Pest(
    val id: Int,
    val name: String,
    val symptoms: List<String>,
    val causes: List<String>,
    val treatment: List<String>,
    val prevention: String,
    val affected_plants: List<String>,
    val isFavorite: Boolean = false // избранное
) 