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
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.plantcare.ui.ReferenceScreen
import com.example.plantcare.ui.PlantCareTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalContext
import com.example.plantcare.util.Prefs

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlantCareApp(viewModel: PlantCareViewModel, aiService: com.example.plantcare.ai.AiService) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
    }
    val context = LocalContext.current
    var darkTheme by remember { mutableStateOf(Prefs.getDarkTheme(context)) }
    PlantCareTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        // Состояния для PlantsScreen
        var showAddPlantDialog by remember { mutableStateOf(false) }
        var showEditPlantDialog by remember { mutableStateOf<Plant?>(null) }
        var showDeletePlantDialog by remember { mutableStateOf<Plant?>(null) }
        var selectedPlant by remember { mutableStateOf<Plant?>(null) }
        var showAddEventDialog by remember { mutableStateOf(false) }
        var showEditEventDialog by remember { mutableStateOf<CareEvent?>(null) }
        var showDeleteEventDialog by remember { mutableStateOf<CareEvent?>(null) }

        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = { slideInHorizontally() },
            exitTransition = { slideOutHorizontally() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
        ) {
            composable(
                route = "home",
                enterTransition = { slideInHorizontally() },
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                HomeScreen(
                    onPlants = { navController.navigate("plants") },
                    onNotes = { navController.navigate("notes") },
                    onReference = { navController.navigate("reference") },
                    onWeather = { navController.navigate("weather") },
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        darkTheme = !darkTheme
                        Prefs.setDarkTheme(context, darkTheme)
                    },
                    onNeural = { navController.navigate("neural") },
                    onDiagnosis = { navController.navigate("diagnosis") },
                    onChatGPT = { navController.navigate("chatgpt_assistant") }
                )
            }
            composable(
                route = "plants",
                enterTransition = { slideInHorizontally() },
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                PlantsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
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
            }
            composable(
                route = "notes",
                enterTransition = { slideInHorizontally() },
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                NotesScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
            }
            composable(
                route = "reference",
                enterTransition = { slideInHorizontally() },
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                ReferenceScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
            }
            composable(
                route = "weather",
                enterTransition = { slideInHorizontally() },
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                WeatherScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "diagnosis",
                enterTransition = { slideInHorizontally() },
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                SymptomDiagnosisScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "neural",
                enterTransition = { slideInHorizontally() },
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                NeuralScreen(onBack = { navController.popBackStack() }, aiService = aiService)
            }
            composable(
                route = "chatgpt_assistant",
                enterTransition = { slideInHorizontally() },
                exitTransition = { slideOutHorizontally() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                ChatGPTAssistantScreen(onBack = { navController.popBackStack() }, aiService = aiService)
            }
        }
    }
}