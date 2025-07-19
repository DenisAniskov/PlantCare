package com.example.plantcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.foundation.background

@Composable
fun HomeScreen(
    onPlants: () -> Unit,
    onNotes: () -> Unit,
    onReference: () -> Unit,
    onWeather: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNeural: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onToggleTheme) {
                if (darkTheme) {
                    Icon(Icons.Filled.LightMode, contentDescription = "Светлая тема", tint = Color.White)
                } else {
                    Icon(Icons.Filled.DarkMode, contentDescription = "Тёмная тема")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("PlantCare", fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
        Text("Создатель: Денис Аниськов", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 32.dp))
        Button(
            onClick = onPlants,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) { Text("Мои растения", fontSize = 22.sp, color = Color.White) }
        Button(
            onClick = onNotes,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) { Text("Заметки", fontSize = 22.sp, color = Color.White) }
        Button(
            onClick = onReference,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) { Text("Справочник", fontSize = 22.sp, color = Color.White) }
        Button(
            onClick = onNeural,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) { Text("Нейросеть", fontSize = 22.sp, color = Color.White) }
        Button(
            onClick = onWeather,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) { Text("Погода", fontSize = 22.sp, color = Color.White) }
    }
} 