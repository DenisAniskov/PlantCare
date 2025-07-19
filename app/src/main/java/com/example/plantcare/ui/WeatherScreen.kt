package com.example.plantcare.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.plantcare.data.Weather
import com.example.plantcare.util.WeatherApi
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import com.example.plantcare.R
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var location by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                getLocation(context, fusedLocationClient) { lat, lon, err ->
                    if (lat != null && lon != null) {
                        location = lat to lon
                    } else {
                        error = err ?: "Не удалось получить координаты"
                    }
                }
            } else {
                error = "Требуется разрешение на геолокацию"
            }
        }
    )

    LaunchedEffect(Unit) {
        val hasPermission = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            getLocation(context, fusedLocationClient) { lat, lon, err ->
                if (lat != null && lon != null) {
                    location = lat to lon
                } else {
                    error = err ?: "Не удалось получить координаты"
                }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(location, refreshTrigger) {
        if (location != null) {
            loading = true
            error = null
            weather = null
            val (lat, lon) = location!!
            val result = withContext(Dispatchers.IO) {
                WeatherApi.getWeather(lat, lon)
            }
            if (result != null) {
                weather = result
            } else {
                error = "Не удалось загрузить погоду"
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Погода", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                weather != null -> WeatherInfo(weather!!, onRefresh = { refreshTrigger++ })
                else -> Text("Ожидание данных о погоде...", fontSize = 18.sp)
            }
        }
    }
}

private fun getLocation(
    context: android.content.Context,
    fusedLocationClient: FusedLocationProviderClient,
    onResult: (Double?, Double?, String?) -> Unit
) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        onResult(null, null, "Нет разрешения на геолокацию")
        return
    }
    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                onResult(loc.latitude, loc.longitude, null)
            } else {
                // Активный запрос, если lastLocation == null
                val locationRequest = LocationRequest.create().apply {
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                    interval = 0
                    fastestInterval = 0
                    numUpdates = 1
                }
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val location = result.lastLocation
                        if (location != null) {
                            onResult(location.latitude, location.longitude, null)
                        } else {
                            onResult(null, null, "Не удалось получить координаты (нет данных)")
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
                fusedLocationClient.requestLocationUpdates(locationRequest, callback, null)
            }
        }.addOnFailureListener {
            onResult(null, null, "Ошибка получения координат: ${it.message}")
        }
    } catch (e: Exception) {
        onResult(null, null, "Ошибка: ${e.message}")
    }
}

@Composable
private fun WeatherInfo(weather: Weather, onRefresh: () -> Unit) {
    val iconRes = when (weather.weatherCode) {
        0 -> R.drawable.ic_weather_clear
        1, 2, 3 -> R.drawable.ic_weather_partly_cloudy
        45, 48 -> R.drawable.ic_weather_fog
        51, 53, 55 -> R.drawable.ic_weather_drizzle
        61, 63, 65 -> R.drawable.ic_weather_rain
        71, 73, 75 -> R.drawable.ic_weather_snow
        80, 81, 82 -> R.drawable.ic_weather_rain
        95, 96, 99 -> R.drawable.ic_weather_thunderstorm
        else -> R.drawable.ic_weather_unknown
    }
    val timeStr = weather.time?.let {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            formatter.format(parser.parse(it)!!)
        } catch (e: Exception) { it }
    } ?: "-"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRefresh, enabled = true, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("Обновить погоду")
        }
        Text("Температура: ${weather.temperature?.let { String.format("%.1f°C", it) } ?: "-"}", fontSize = 22.sp)
        Text("${weather.description ?: "-"}", fontSize = 20.sp)
        if (weather.pressure != null) {
            Text("Давление: ${weather.pressure.toInt()} гПа", fontSize = 18.sp)
        }
        if (weather.humidity != null) {
            Text("Влажность: ${weather.humidity.toInt()}%", fontSize = 18.sp)
        }
        if (weather.windSpeed != null) {
            Text("Ветер: ${weather.windSpeed} м/с", fontSize = 18.sp)
        }
        Text("Время обновления: $timeStr", fontSize = 16.sp, color = Color.Gray)
        if (weather.lat != null && weather.lon != null) {
            Text("Координаты: ${String.format("%.4f", weather.lat)}, ${String.format("%.4f", weather.lon)}", fontSize = 16.sp, color = Color.Gray)
        }
    }
} 