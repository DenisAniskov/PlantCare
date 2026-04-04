package com.example.plantcare.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.example.plantcare.data.Disease
import com.example.plantcare.data.Pest
import com.example.plantcare.data.ReferencePlant
import com.example.plantcare.util.DiseaseJsonImporter
import com.example.plantcare.util.PestJsonImporter
import com.example.plantcare.util.PerenualApi
import com.example.plantcare.util.PixabayApi
import com.example.plantcare.util.PlantJsonImporter
import com.example.plantcare.ui.components.ProxyIndicator
import com.example.plantcare.ui.components.ProxyStatusBottomSheet
import com.example.plantcare.viewmodel.PlantCareViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceScreen(onBack: () -> Unit, viewModel: PlantCareViewModel) {
    val referencePlants by viewModel.referencePlants.collectAsState()
    var diseases by remember { mutableStateOf<List<Disease>>(emptyList()) }
    var pests by remember { mutableStateOf<List<Pest>>(emptyList()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try { diseases = DiseaseJsonImporter.importDiseases(context) } catch (_: Exception) { }
        try { pests = PestJsonImporter.importPests(context) } catch (_: Exception) { }
    }

    var query by remember { mutableStateOf("") }
    var searchTriggered by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var perenualResults by remember { mutableStateOf<List<PerenualApi.PlantCareInfo>>(emptyList()) }

    // Cache for Pixabay images: plant name -> image URL
    val imageCache = remember { mutableMapOf<String, String?>() }

    // Локальный поиск — основной источник (растения, болезни, вредители)
    val (plantMatches, diseaseMatches, pestMatches) = remember(searchTriggered, referencePlants, diseases, pests) {
        val q = (searchTriggered ?: "").trim().lowercase()
        if (q.isBlank()) return@remember Triple(emptyList<ReferencePlant>(), emptyList<Disease>(), emptyList<Pest>())
        val plants = referencePlants.filter { p ->
            p.name.lowercase().contains(q) || (p.description.orEmpty()).lowercase().contains(q) ||
            (p.watering.orEmpty()).lowercase().contains(q) || (p.light.orEmpty()).lowercase().contains(q) ||
            (p.fertilizing.orEmpty()).lowercase().contains(q) || (p.notes.orEmpty()).lowercase().contains(q)
        }
        val dis = diseases.filter { d ->
            d.name.lowercase().contains(q) || d.affected_plants.joinToString(", ").lowercase().contains(q) ||
            d.symptoms.joinToString(", ").lowercase().contains(q) || d.causes.joinToString(", ").lowercase().contains(q) ||
            d.treatment.joinToString(", ").lowercase().contains(q) || d.prevention.lowercase().contains(q)
        }
        val pes = pests.filter { p ->
            p.name.lowercase().contains(q) || p.affected_plants.joinToString(", ").lowercase().contains(q) ||
            p.symptoms.joinToString(", ").lowercase().contains(q) || p.treatment.joinToString(", ").lowercase().contains(q) ||
            p.prevention.lowercase().contains(q)
        }
        Triple(plants, dis, pes)
    }
    val hasLocalResults = plantMatches.isNotEmpty() || diseaseMatches.isNotEmpty() || pestMatches.isNotEmpty()

    // Perenual API — fallback, когда локально ничего не найдено
    LaunchedEffect(searchTriggered, hasLocalResults) {
        val q = searchTriggered ?: return@LaunchedEffect
        if (q.isBlank()) {
            perenualResults = emptyList()
            return@LaunchedEffect
        }
        if (hasLocalResults) {
            loading = false
            perenualResults = emptyList()
            return@LaunchedEffect
        }
        loading = true
        perenualResults = emptyList()
        try {
            val perenual = PerenualApi.searchPlants(q, context)
            perenualResults = perenual
        } catch (_: Exception) { }
        loading = false
    }

    // Load Pixabay images for local plant matches
    LaunchedEffect(plantMatches) {
        plantMatches.forEach { plant ->
            if (!imageCache.containsKey(plant.name)) {
                imageCache[plant.name] = null // placeholder
                val result = PixabayApi.searchPlantImage(plant.name)
                imageCache[plant.name] = result?.webUrl
            }
        }
    }

    val showLocal = hasLocalResults
    val showPerenual = perenualResults.isNotEmpty() && !hasLocalResults
    val isSearching = searchTriggered?.isNotBlank() == true && !hasLocalResults && perenualResults.isEmpty() && loading

    var showProxyDetails by remember { mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск по справочнику", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    ProxyIndicator(onShowDetails = { showProxyDetails = true })
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Растение, болезнь или вредитель") },
                placeholder = { Text("Например: роза, фитофтороз, тля") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { searchTriggered = query.trim() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Search, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (loading) "Поиск…" else "Искать")
            }
            Spacer(Modifier.height(16.dp))
            when {
                searchTriggered.isNullOrBlank() -> Text(
                    "Введите запрос и нажмите «Искать». Сначала локальная база, при отсутствии — Perenual.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                isSearching || loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                showPerenual -> PerenualResultsCard(
                    results = perenualResults,
                    imageCache = imageCache,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                showLocal -> LazyColumn(Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("Из локальной базы:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    items(plantMatches) { plant ->
                        LocalPlantCard(
                            plant = plant,
                            imageUrl = imageCache[plant.name]
                        )
                    }
                    items(diseaseMatches) { d ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("🦠 ${d.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Поражает: ${d.affected_plants.joinToString(", ")}", Modifier.padding(top = 4.dp))
                                Text("Симптомы: ${d.symptoms.joinToString(", ")}", Modifier.padding(top = 2.dp))
                                Text("Лечение: ${d.treatment.joinToString(", ")}", Modifier.padding(top = 2.dp))
                                Text("Профилактика: ${d.prevention}", Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                    items(pestMatches) { p ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("🐛 ${p.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Поражает: ${p.affected_plants.joinToString(", ")}", Modifier.padding(top = 4.dp))
                                Text("Симптомы: ${p.symptoms.joinToString(", ")}", Modifier.padding(top = 2.dp))
                                Text("Лечение: ${p.treatment.joinToString(", ")}", Modifier.padding(top = 2.dp))
                                Text("Профилактика: ${p.prevention}", Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
                !showLocal && perenualResults.isEmpty() && !loading -> Text(
                    "Ничего не найдено по запросу «$query».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LocalPlantCard(plant: ReferencePlant, imageUrl: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            // Image section
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = plant.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text("🌿 ${plant.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (!plant.description.isNullOrBlank()) Text(plant.description ?: "", Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!plant.watering.isNullOrBlank()) Text("💧 ${plant.watering}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!plant.light.isNullOrBlank()) Text("☀️ ${PlantJsonImporter.translateLight(plant.light)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!plant.temperature.isNullOrBlank()) Text("🌡️ ${plant.temperature}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!plant.fertilizing.isNullOrBlank()) Text("🌱 ${plant.fertilizing}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!plant.notes.isNullOrBlank()) Text("📝 ${plant.notes}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PerenualResultsCard(results: List<PerenualApi.PlantCareInfo>, imageCache: MutableMap<String, String?>, modifier: Modifier = Modifier) {
    // Load images for Perenual results
    LaunchedEffect(results) {
        results.forEach { plant ->
            val key = plant.name.ifBlank { plant.scientificName }
            if (!imageCache.containsKey(key)) {
                imageCache[key] = null
                val result = PixabayApi.searchPlantImage(key)
                imageCache[key] = result?.webUrl
            }
        }
    }

    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Результаты из базы растений (Perenual)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            results.forEach { plant ->
                val imageUrl = imageCache[plant.name.ifBlank { plant.scientificName }]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column {
                        if (!imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = plant.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text("🌿 ${plant.name.ifBlank { plant.scientificName }}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (plant.scientificName.isNotBlank()) {
                                Text(plant.scientificName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                                if (plant.watering.isNotBlank()) Text("💧 ${plant.watering}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (plant.sunlight.isNotBlank()) Text("☀️ ${plant.sunlight}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (plant.careLevel.isNotBlank()) {
                                Text("🌱 ${plant.careLevel}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
