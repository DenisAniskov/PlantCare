package com.example.plantcare.data

data class Weather(
    val temperature: Double?,
    val weatherCode: Int?,
    val windSpeed: Double?,
    val humidity: Double?,
    val pressure: Double?,
    val time: String?,
    val lat: Double?,
    val lon: Double?,
    val description: String?
) 