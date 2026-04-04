package com.example.plantcare.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background

/**
 * Общий HomeScreen для Android и Desktop
 */
@Composable
fun SharedHomeScreen(
    onPlants: () -> Unit,
    onNotes: () -> Unit,
    onReference: () -> Unit,
    onWeather: () -> Unit,
    onNeural: () -> Unit,
    onDiagnosis: () -> Unit,
    onAssistant: () -> Unit,
    darkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(PlantCareDesign.Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onToggleTheme != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (darkTheme) "Светлая тема" else "Тёмная тема"
                    )
                }
            }
            Spacer(modifier = Modifier.height(PlantCareDesign.Spacing.Medium))
        } else {
            Spacer(modifier = Modifier.height(PlantCareDesign.Spacing.ExtraLarge))
        }
        
        ScreenTitle(text = "PlantCare")
        Subtitle(
            text = "Создатель: Денис Аниськов",
            modifier = Modifier.padding(bottom = PlantCareDesign.Spacing.ExtraLarge)
        )
        
        PlantCareButton(text = "Мои растения", onClick = onPlants)
        PlantCareButton(text = "Заметки", onClick = onNotes)
        PlantCareButton(text = "Справочник", onClick = onReference)
        PlantCareButton(text = "Нейросеть", onClick = onNeural)
        PlantCareButton(text = "Диагностика", onClick = onDiagnosis)
        PlantCareButton(text = "ИИ-ассистент", onClick = onAssistant)
        PlantCareButton(text = "Погода", onClick = onWeather)
        
        Spacer(modifier = Modifier.weight(1f))
    }
}
