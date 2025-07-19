package com.example.plantcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantcare.data.Plant
import com.example.plantcare.data.Disease
import com.example.plantcare.util.PlantJsonImporter
import com.example.plantcare.util.DiseaseJsonImporter
import androidx.compose.ui.text.font.FontWeight
import com.example.plantcare.data.ReferencePlant
import com.example.plantcare.data.Pest
import com.example.plantcare.util.PestJsonImporter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import android.content.Context
import android.content.SharedPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceScreen(onBack: () -> Unit, viewModel: com.example.plantcare.viewmodel.PlantCareViewModel) {
    var tab by remember { mutableStateOf(0) } // 0 - растения, 1 - болезни, 2 - вредители
    val referencePlants by viewModel.referencePlants.collectAsState()
    var diseases by remember { mutableStateOf<List<Disease>>(emptyList()) }
    var pests by remember { mutableStateOf<List<Pest>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("favorites", Context.MODE_PRIVATE) }
    fun getFavoriteIds(key: String): Set<Int> = prefs.getStringSet(key, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    fun setFavoriteIds(key: String, ids: Set<Int>) = prefs.edit().putStringSet(key, ids.map { it.toString() }.toSet()).apply()

    // Загрузка болезней и вредителей при переключении вкладки
    LaunchedEffect(tab) {
        if (tab == 1) {
            try {
                val loaded = DiseaseJsonImporter.importDiseases(context)
                val favIds = getFavoriteIds("disease_favs")
                diseases = loaded.map { it.copy(isFavorite = favIds.contains(it.id)) }
                errorMessage = if (loaded.isEmpty()) "Нет данных о болезнях или ошибка загрузки файла." else null
            } catch (e: Exception) {
                diseases = emptyList()
                errorMessage = "Ошибка загрузки болезней: ${e.localizedMessage}"
            }
        } else if (tab == 2) {
            try {
                val loaded = PestJsonImporter.importPests(context)
                val favIds = getFavoriteIds("pest_favs")
                pests = loaded.map { it.copy(isFavorite = favIds.contains(it.id)) }
                errorMessage = if (loaded.isEmpty()) "Нет данных о вредителях или ошибка загрузки файла." else null
            } catch (e: Exception) {
                pests = emptyList()
                errorMessage = "Ошибка загрузки вредителей: ${e.localizedMessage}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Справочник", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onBack) {
                    Text("Назад")
                }
                Text("Справочник", fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {
                    tab = 0
                    errorMessage = null
                }, colors = ButtonDefaults.buttonColors(if (tab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)) {
                    Text("Растения")
                }
                Button(onClick = {
                    tab = 1
                    errorMessage = null
                }, colors = ButtonDefaults.buttonColors(if (tab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)) {
                    Text("Болезни")
                }
                Button(onClick = {
                    tab = 2
                    errorMessage = null
                }, colors = ButtonDefaults.buttonColors(if (tab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)) {
                    Text("Вредители")
                }
            }
            Spacer(Modifier.height(16.dp))
            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
            } else when (tab) {
                0 -> {
                    if (referencePlants.isEmpty()) {
                        Text("Нет данных о растениях или ошибка загрузки файла.", color = MaterialTheme.colorScheme.error)
                    } else {
                        val sortedPlants = referencePlants.sortedByDescending { it.isFavorite }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(sortedPlants) { plant ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(8.dp)) {
                                        Text(plant.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (!plant.description.isNullOrBlank()) Text(plant.description)
                                        if (!plant.watering.isNullOrBlank()) Text("Полив: ${plant.watering}")
                                        if (!plant.light.isNullOrBlank()) Text("Освещение: ${com.example.plantcare.util.PlantJsonImporter.translateLight(plant.light)}")
                                        if (!plant.temperature.isNullOrBlank()) Text("Температура: ${plant.temperature}")
                                        if (!plant.fertilizing.isNullOrBlank()) Text("Подкормка: ${plant.fertilizing}")
                                        if (!plant.notes.isNullOrBlank()) Text("Заметки: ${plant.notes}")
                                        }
                                        IconButton(onClick = {
                                            viewModel.toggleReferencePlantFavorite(plant)
                                        }) {
                                            Icon(
                                                imageVector = if (plant.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                contentDescription = if (plant.isFavorite) "Убрать из избранного" else "В избранное",
                                                tint = if (plant.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (diseases.isEmpty()) {
                        Text("Нет данных о болезнях или ошибка загрузки файла.", color = MaterialTheme.colorScheme.error)
                    } else {
                        var diseasesState by remember { mutableStateOf(diseases) }
                        LaunchedEffect(diseases) { diseasesState = diseases }
                        val sortedDiseases = diseasesState.sortedByDescending { it.isFavorite }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(sortedDiseases) { disease ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(8.dp)) {
                                        Text(disease.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Поражает: " + disease.affected_plants.joinToString(", "))
                                        Text("Симптомы: " + disease.symptoms.joinToString(", "))
                                        Text("Причины: " + disease.causes.joinToString(", "))
                                        Text("Лечение: " + disease.treatment.joinToString(", "))
                                        Text("Профилактика: ${disease.prevention}")
                                        }
                                        IconButton(onClick = {
                                            val newList = diseasesState.map {
                                                if (it.id == disease.id) it.copy(isFavorite = !it.isFavorite) else it
                                            }
                                            diseasesState = newList
                                            setFavoriteIds("disease_favs", newList.filter { it.isFavorite }.map { it.id }.toSet())
                                        }) {
                                            Icon(
                                                imageVector = if (disease.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                contentDescription = if (disease.isFavorite) "Убрать из избранного" else "В избранное",
                                                tint = if (disease.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (pests.isEmpty()) {
                        Text("Нет данных о вредителях или ошибка загрузки файла.", color = MaterialTheme.colorScheme.error)
                    } else {
                        var pestsState by remember { mutableStateOf(pests) }
                        LaunchedEffect(pests) { pestsState = pests }
                        val sortedPests = pestsState.sortedByDescending { it.isFavorite }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(sortedPests) { pest ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(8.dp)) {
                                        Text(pest.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("Поражает: " + pest.affected_plants.joinToString(", "))
                                        Text("Симптомы: " + pest.symptoms.joinToString(", "))
                                        Text("Причины: " + pest.causes.joinToString(", "))
                                        Text("Лечение: " + pest.treatment.joinToString(", "))
                                        Text("Профилактика: ${pest.prevention}")
                                        }
                                        IconButton(onClick = {
                                            val newList = pestsState.map {
                                                if (it.id == pest.id) it.copy(isFavorite = !it.isFavorite) else it
                                            }
                                            pestsState = newList
                                            setFavoriteIds("pest_favs", newList.filter { it.isFavorite }.map { it.id }.toSet())
                                        }) {
                                            Icon(
                                                imageVector = if (pest.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                contentDescription = if (pest.isFavorite) "Убрать из избранного" else "В избранное",
                                                tint = if (pest.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} 