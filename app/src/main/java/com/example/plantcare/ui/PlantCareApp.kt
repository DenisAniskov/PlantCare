package com.example.plantcare.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.example.plantcare.viewmodel.PlantCareViewModel
import com.example.plantcare.data.Plant
import com.example.plantcare.data.CareEvent
import com.example.plantcare.data.ReferencePlant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.sp
import com.example.plantcare.ui.ReferenceScreen
import com.example.plantcare.ui.PlantCareTheme
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun PlantCareApp(viewModel: PlantCareViewModel) {
    var darkTheme by remember { mutableStateOf(false) }
    PlantCareTheme(darkTheme = darkTheme) {
        var currentScreen by remember { mutableStateOf("home") }
        // Состояния для PlantsScreen
    var showAddPlantDialog by remember { mutableStateOf(false) }
    var showEditPlantDialog by remember { mutableStateOf<Plant?>(null) }
    var showDeletePlantDialog by remember { mutableStateOf<Plant?>(null) }
    var selectedPlant by remember { mutableStateOf<Plant?>(null) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var showEditEventDialog by remember { mutableStateOf<CareEvent?>(null) }
    var showDeleteEventDialog by remember { mutableStateOf<CareEvent?>(null) }

        when (currentScreen) {
            "home" -> HomeScreen(
                onPlants = { currentScreen = "plants" },
                onNotes = { currentScreen = "notes" },
                onReference = { currentScreen = "reference" },
                onWeather = { currentScreen = "weather" },
                darkTheme = darkTheme,
                onToggleTheme = { darkTheme = !darkTheme },
                onNeural = { currentScreen = "neural" }
            )
            "plants" -> PlantsScreen(
                viewModel = viewModel,
                onBack = { currentScreen = "home" },
                showAddPlantDialog = showAddPlantDialog,
                setShowAddPlantDialog = { showAddPlantDialog = it },
                showEditPlantDialog = showEditPlantDialog,
                setShowEditPlantDialog = { showEditPlantDialog = it },
                showDeletePlantDialog = showDeletePlantDialog,
                setShowDeletePlantDialog = { showDeletePlantDialog = it },
                selectedPlant = selectedPlant,
                setSelectedPlant = { selectedPlant = it },
                showAddEventDialog = showAddEventDialog,
                setShowAddEventDialog = { showAddEventDialog = it },
                showEditEventDialog = showEditEventDialog,
                setShowEditEventDialog = { showEditEventDialog = it },
                showDeleteEventDialog = showDeleteEventDialog,
                setShowDeleteEventDialog = { showDeleteEventDialog = it }
            )
            "notes" -> NotesScreen(onBack = { currentScreen = "home" }, viewModel = viewModel)
            "reference" -> ReferenceScreen(onBack = { currentScreen = "home" }, viewModel = viewModel)
            "weather" -> WeatherScreen(onBack = { currentScreen = "home" })
            "neural" -> NeuralScreen(onBack = { currentScreen = "home" })
        }
    }
} 