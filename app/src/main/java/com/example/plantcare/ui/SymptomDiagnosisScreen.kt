package com.example.plantcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantcare.ai.AiClientProvider
import com.example.plantcare.core.LocalRagEngine
import com.example.plantcare.data.Disease
import com.example.plantcare.util.DiseaseJsonImporter
import com.example.plantcare.ui.components.ProxyIndicator
import com.example.plantcare.ui.components.ProxyStatusBottomSheet
import com.example.plantcare.sharedui.AssistantMessageContent
import java.io.BufferedReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomDiagnosisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun readAsset(name: String): String = try {
        context.assets.open(name).bufferedReader().use(BufferedReader::readText)
    } catch (_: Exception) { "[]" }

    val engine = remember {
        LocalRagEngine(
            plantsJson = readAsset("plants.json"),
            pestsJson = readAsset("pests.json"),
            diseasesJson = readAsset("diseases.json"),
            tipsJson = readAsset("plant_care_tips.json")
        )
    }
    val aiClient = remember { AiClientProvider.getVisionClient(context) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(aiClient) {
        aiClient.setStatusCallback { status ->
            statusMessage = status
        }
    }

    var diseases by remember { mutableStateOf<List<Disease>>(emptyList()) }
    LaunchedEffect(Unit) {
        try { diseases = DiseaseJsonImporter.importDiseases(context) } catch (_: Exception) { }
    }

    var placeByUser by remember { mutableStateOf("") }
    var whatHappens by remember { mutableStateOf("") }
    var plantName by remember { mutableStateOf("") }
    var analyzeCounter by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var localResults by remember { mutableStateOf<List<LocalRagEngine.Snippet>>(emptyList()) }
    var aiDiagnosis by remember { mutableStateOf<String?>(null) }
    var usedAiFallback by remember { mutableStateOf(false) }
    var showProxyDetails by remember { mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
    }

    LaunchedEffect(analyzeCounter) {
        if (analyzeCounter == 0) return@LaunchedEffect
        val parts = listOf(placeByUser.trim(), whatHappens.trim(), plantName.trim()).filter { it.isNotBlank() }
        val query = parts.joinToString(" ")
        if (query.isBlank()) {
            localResults = emptyList()
            aiDiagnosis = null
            return@LaunchedEffect
        }
        loading = true
        aiDiagnosis = null
        usedAiFallback = false
        statusMessage = null

        val localQuery = listOf(
            placeByUser.trim().takeIf { it.isNotBlank() }?.let { "место: $it" },
            whatHappens.trim().takeIf { it.isNotBlank() }?.let { "симптомы: $it" },
            plantName.trim().takeIf { it.isNotBlank() }?.let { "растение: $it" }
        ).filterNotNull().joinToString(" ")
        localResults = engine.searchDiseases(localQuery, limit = 5)

        if (localResults.isEmpty()) {
            val prompt = buildString {
                append("Ты — эксперт по болезням растений. Проанализируй симптомы и определи возможное заболевание.\n\n")
                if (plantName.isNotBlank()) append("Растение: $plantName\n")
                if (placeByUser.isNotBlank()) append("Место проявления: $placeByUser\n")
                if (whatHappens.isNotBlank()) append("Симптомы: $whatHappens\n")
                append("\nОтветь на русском языке в формате:\n")
                append("**Болезнь:** название\n")
                append("**Описание:** краткое описание\n")
                append("**Симптомы:** перечисление\n")
                append("**Лечение:** пошаговые рекомендации\n")
                append("**Профилактика:** меры профилактики")
            }
            val result = aiClient.sendMessage(
                history = emptyList(),
                userText = prompt,
                imageBase64 = null,
                systemPrompt = "Ты — ассистент по диагностике болезней растений. Отвечай точно и по делу на русском. Если диагностируешь болезнь — добавь в самом конце, отдельной строкой: KEYWORDS: название болезни на английском"
            )
            aiDiagnosis = result.getOrNull()
            usedAiFallback = true
        }
        loading = false
    }

    fun findDiseaseByTitle(title: String): Disease? = diseases.find { it.name.equals(title, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Диагностика болезней", fontSize = 22.sp) },
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
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Status message - shows real-time model status updates
            statusMessage?.let { status ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text("Укажите по возможности: место проявления, что происходит и растение. Затем нажмите «Анализировать».", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = placeByUser,
                onValueChange = { placeByUser = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Место (листья, стебель, корни, цветы, плоды…)") },
                placeholder = { Text("Например: на листьях, у корня") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = whatHappens,
                onValueChange = { whatHappens = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                label = { Text("Что происходит (симптомы)") },
                placeholder = { Text("Пятна, увядание, пожелтение, налёт, дырочки…") },
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = plantName,
                onValueChange = { plantName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Растение (если известно)") },
                placeholder = { Text("Томат, роза, огурец…") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { analyzeCounter++ },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (loading) "Анализ…" else "Анализировать")
            }
            Spacer(Modifier.height(20.dp))

            if (localResults.isNotEmpty() || aiDiagnosis != null) {
                Text("Результаты диагностики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    localResults.forEach { snippet ->
                        val disease = findDiseaseByTitle(snippet.title)
                        if (disease != null) {
                            DiseaseResultCard(disease)
                        } else {
                            SnippetResultCard(snippet)
                        }
                    }
                    aiDiagnosis?.let { text ->
                        AiDiagnosisCard(text)
                    }
                }
            } else if (analyzeCounter > 0 && !loading) {
                val hasInput = placeByUser.isNotBlank() || whatHappens.isNotBlank() || plantName.isNotBlank()
                if (hasInput) {
                    Text("По введённым симптомам совпадений не найдено. Уточните место, описание или название растения.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                } else {
                    Text("Заполните хотя бы одно поле (место, симптомы или растение) и нажмите «Анализировать».", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun DiseaseResultCard(d: Disease) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text("Болезнь / причина", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(d.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (d.affected_plants.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Поражает: ${d.affected_plants.joinToString(", ")}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text("Симптомы:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            d.symptoms.forEach { Text("• $it", Modifier.padding(vertical = 2.dp), fontSize = 14.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Чем лечить:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            d.treatment.forEachIndexed { i, s -> Text("${i + 1}. $s", Modifier.padding(vertical = 2.dp), fontSize = 14.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Профилактика:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(d.prevention, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AiDiagnosisCard(text: String) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("AI-диагностика", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            AssistantMessageContent(
                text = text,
                textColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SnippetResultCard(snippet: LocalRagEngine.Snippet) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text("Вероятная причина / совпадение", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(snippet.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(snippet.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
