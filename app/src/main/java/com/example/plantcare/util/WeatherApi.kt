package com.example.plantcare.util

import android.util.Log
import com.example.plantcare.data.Weather
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request

object WeatherApi {
    private val client = OkHttpClient()
    private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

    fun getWeather(lat: Double, lon: Double): Weather? {
        val url = "$BASE_URL?latitude=$lat&longitude=$lon&current_weather=true&hourly=relative_humidity_2m,pressure_msl"
        val request = Request.Builder().url(url).build()
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Weather(
                    temperature = null,
                    weatherCode = null,
                    windSpeed = null,
                    humidity = null,
                    pressure = null,
                    time = null,
                    lat = lat,
                    lon = lon,
                    description = "Ошибка HTTP: ${response.code}"
                )
            }
            val body = response.body?.string() ?: return Weather(
                temperature = null,
                weatherCode = null,
                windSpeed = null,
                humidity = null,
                pressure = null,
                time = null,
                lat = lat,
                lon = lon,
                description = "Пустой ответ от сервера"
            )
            val json = Gson().fromJson(body, OpenMeteoResponse::class.java)
            val current = json.current_weather
            val humidity = json.hourly?.relative_humidity_2m?.getOrNull(0)
            val pressure = json.hourly?.pressure_msl?.getOrNull(0)
            Weather(
                temperature = current?.temperature,
                weatherCode = current?.weathercode,
                windSpeed = current?.windspeed,
                humidity = humidity,
                pressure = pressure,
                time = current?.time,
                lat = lat,
                lon = lon,
                description = weatherCodeToDescription(current?.weathercode)
            )
        } catch (e: Exception) {
            Log.e("WeatherApi", "Ошибка получения погоды: ${e.message}", e)
            return Weather(
                temperature = null,
                weatherCode = null,
                windSpeed = null,
                humidity = null,
                pressure = null,
                time = null,
                lat = lat,
                lon = lon,
                description = "Ошибка: ${e.message}"
            )
        }
    }

    private fun weatherCodeToDescription(code: Int?): String? {
        return when (code) {
            0 -> "Ясно"
            1, 2, 3 -> "Переменная облачность"
            45, 48 -> "Туман"
            51, 53, 55 -> "Морось"
            61, 63, 65 -> "Дождь"
            71, 73, 75 -> "Снег"
            80, 81, 82 -> "Ливень"
            95, 96, 99 -> "Гроза"
            else -> "Неизвестно"
        }
    }

    private data class OpenMeteoResponse(
        val current_weather: CurrentWeather?,
        val hourly: HourlyData?
    ) {
        data class CurrentWeather(
            val temperature: Double?,
            val weathercode: Int?,
            val windspeed: Double?,
            val time: String?
        )
        data class HourlyData(
            val relative_humidity_2m: List<Double>?,
            val pressure_msl: List<Double>?
        )
    }
} 