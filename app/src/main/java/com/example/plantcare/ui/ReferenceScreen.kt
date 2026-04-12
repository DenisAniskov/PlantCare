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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp

import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

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
    var perenualResults by remember { mutableStateOf<List<PerenualApi.PerenualResult>>(emptyList()) }
    var selectedPerenualItem by remember { mutableStateOf<PerenualApi.PerenualResult?>(null) }
    var loadingDetails by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var translateEnabled by remember { mutableStateOf(true) }

    // Cache for Pixabay images: plant name -> image URL
    val imageCache = remember { mutableMapOf<String, String?>() }

    // Локальный поиск — только для вкладки 0
    val (plantMatches, diseaseMatches, pestMatches) = remember(searchTriggered, referencePlants, diseases, pests, selectedTab) {
        val q = (searchTriggered ?: "").trim().lowercase()
        if (q.isBlank() || selectedTab != 0) return@remember Triple(emptyList<ReferencePlant>(), emptyList<Disease>(), emptyList<Pest>())
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

    // Perenual API — только для вкладки 1
    LaunchedEffect(searchTriggered, selectedTab) {
        val q = searchTriggered ?: return@LaunchedEffect
        if (q.isBlank() || selectedTab != 1) {
            perenualResults = emptyList()
            return@LaunchedEffect
        }
        loading = true
        perenualResults = emptyList()
        try {
            perenualResults = PerenualApi.searchAll(q, context, translateEnabled)
        } catch (_: Exception) { }
        loading = false
    }

    // Load full details for selected item
    LaunchedEffect(selectedPerenualItem?.id) {
        val item = selectedPerenualItem ?: return@LaunchedEffect
        if (item.description.isNotBlank() || item.watering.isNotBlank()) return@LaunchedEffect // already loaded
        
        loadingDetails = true
        try {
            val full = PerenualApi.getFullDetails(item.id, item.isDisease, context, translateEnabled)
            if (full != null) {
                selectedPerenualItem = full
            }
        } catch (_: Exception) {}
        loadingDetails = false
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

    var showProxyDetails by remember { mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Справочник", fontSize = 22.sp) },
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
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(bottom = 16.dp)) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; searchTriggered = null; query = "" }) {
                    Text("Офлайн", modifier = Modifier.padding(8.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; searchTriggered = null; query = "" }) {
                    Text("Онлайн(Perenual)", modifier = Modifier.padding(8.dp))
                }
            }

            if (selectedTab == 1) {
                Text(
                    "Для лучшего результата пишите название на английском",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (selectedTab == 0) "Растение, болезнь или вредитель" else "Название растения") },
                placeholder = { Text(if (selectedTab == 0) "Например: роза, тля" else "Например: Monstera") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            
            if (selectedTab == 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Checkbox(
                        checked = translateEnabled,
                        onCheckedChange = { translateEnabled = it }
                    )
                    Text(
                        "Переводить на русский (ИИ)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

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

            val hasLocalResults = plantMatches.isNotEmpty() || diseaseMatches.isNotEmpty() || pestMatches.isNotEmpty()
            val showLocal = hasLocalResults && selectedTab == 0
            val showPerenual = perenualResults.isNotEmpty() && selectedTab == 1

            when {
                searchTriggered.isNullOrBlank() -> Text(
                    if (selectedTab == 0) "Поиск по встроенной базе знаний." else "Поиск по глобальной базе Perenual.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                showPerenual -> PerenualResultsCard(
                    results = perenualResults,
                    selectedPerenualItem = selectedPerenualItem,
                    onItemClick = { clicked -> 
                        if (selectedPerenualItem?.id == clicked.id) {
                            selectedPerenualItem = null
                        } else {
                            selectedPerenualItem = clicked
                        }
                    },
                    loadingDetails = loadingDetails,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                showLocal -> LazyColumn(Modifier.fillMaxSize().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                !loading -> Text(
                    "Ничего не найдено по запросу «$searchTriggered».",
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
private fun PerenualResultsCard(
    results: List<PerenualApi.PerenualResult>, 
    selectedPerenualItem: PerenualApi.PerenualResult?,
    onItemClick: (PerenualApi.PerenualResult) -> Unit,
    loadingDetails: Boolean,
    modifier: Modifier = Modifier
) {
    var cachedFullItem by remember { mutableStateOf<PerenualApi.PerenualResult?>(null) }
    
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Кэшируем полные данные, чтобы при сворачивании/разворачивании они не пропадали
    LaunchedEffect(selectedPerenualItem) {
        if (selectedPerenualItem != null && selectedPerenualItem.description.isNotBlank()) {
            cachedFullItem = selectedPerenualItem
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Text("Результаты из базы Perenual (всемирная база)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text("Нажмите на карточку 👆 чтобы узнать подробности", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        
        results.forEach { item ->
            val isSelected = selectedPerenualItem?.id == item.id
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { onItemClick(item) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.isDisease) "🦠" else "🌿", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (item.scientificName.isNotBlank()) {
                                Text(item.scientificName, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        if (isSelected && !loadingDetails) {
                            IconButton(onClick = {
                                val displayItem = if (selectedPerenualItem != null) selectedPerenualItem else item
                                val textToCopy = buildString {
                                    appendLine("Название: ${displayItem.name}")
                                    if (displayItem.scientificName.isNotBlank()) appendLine("Научное название: ${displayItem.scientificName}")
                                    if (displayItem.description.isNotBlank()) appendLine("\nОписание:\n${displayItem.description}")
                                    if (displayItem.isDisease) {
                                        if (!displayItem.solution.isNullOrBlank()) appendLine("\nРешение:\n${displayItem.solution}")
                                    } else {
                                        appendLine("\nУход:")
                                        appendLine("💧 Полив: ${displayItem.watering.ifBlank { "N/A" }}")
                                        if (!displayItem.benchmark.isNullOrBlank()) appendLine("   Интервал: ${displayItem.benchmark}")
                                        appendLine("☀️ Свет: ${displayItem.sunlight.ifBlank { "N/A" }}")
                                        if (displayItem.careLevel.isNotBlank()) appendLine("🌱 Уровень ухода: ${displayItem.careLevel}")
                                    }
                                    appendLine("\nИсточник: Perenual API")
                                }
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                Toast.makeText(context, "Информация скопирована", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(20.dp))
                            }
                        }

                        if (isSelected && loadingDetails) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.KeyboardArrowUp else Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isSelected && !loadingDetails) {
                        // Используем кэшированные данные, если текущие еще не подгрузились или ID совпадает
                        val displayItem = if (isSelected && selectedPerenualItem != null) selectedPerenualItem else item

                        Column(Modifier.padding(top = 12.dp)) {
                            if (!displayItem.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = displayItem.imageUrl,
                                    contentDescription = displayItem.name,
                                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            
                            if (displayItem.description.isNotBlank()) {
                                Text("Описание:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(displayItem.description, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }

                            if (displayItem.isDisease && !displayItem.solution.isNullOrBlank()) {
                                Text("💡 Решение:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(displayItem.solution, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }

                            if (!displayItem.isDisease) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text("💧 Полив", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(displayItem.watering.ifBlank { "N/A" }, fontSize = 13.sp)
                                        if (!displayItem.benchmark.isNullOrBlank()) {
                                            Text(displayItem.benchmark!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("☀️ Свет", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(displayItem.sunlight.ifBlank { "N/A" }, fontSize = 13.sp)
                                    }
                                }
                                if (displayItem.careLevel.isNotBlank()) {
                                    Text("🌱 Уровень ухода: ${displayItem.careLevel}", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
