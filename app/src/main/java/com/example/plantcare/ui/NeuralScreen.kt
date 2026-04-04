package com.example.plantcare.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.plantcare.ai.AiClientProvider
import com.example.plantcare.sharedui.AssistantMessageContent
import kotlinx.coroutines.flow.onEach
import com.example.plantcare.ai.AiService
import com.example.plantcare.ai.PlantAnalysisPrompt
import com.example.plantcare.ai.PlantAnalysisResult
import com.example.plantcare.ui.components.ProxyIndicator
import com.example.plantcare.ui.components.ProxyStatusBottomSheet
import com.example.plantcare.util.Prefs
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.ByteArrayOutputStream
import android.graphics.Color
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuralScreen(onBack: () -> Unit, aiService: AiService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showProxyDetails by remember { mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
    }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var analysis by remember { mutableStateOf<PlantAnalysisResult?>(null) }
    var tflitePredictions by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var usedTfliteFallback by remember { mutableStateOf(false) }
    var rawAiResponse by remember { mutableStateOf<String?>(null) }

    val classNames = remember {
        runCatching { loadClassNames(context) }.getOrDefault(emptyList())
    }
    val interpreter: Interpreter? = remember {
        runCatching { Interpreter(loadModelFile(context, "plant_disease_mobilenetv2.tflite")) }.getOrNull()
    }
    DisposableEffect(interpreter) {
        onDispose { interpreter?.close() }
    }

    val aiClient = remember { AiClientProvider.get(context) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(aiClient) {
        aiClient.setStatusCallback { status ->
            statusMessage = status
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        imageUri = uri
        analysis = null
        tflitePredictions = emptyList()
        error = null
        usedTfliteFallback = false
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        val uri = saveBitmapToUri(context, bitmap)
        imageUri = uri
        analysis = null
        tflitePredictions = emptyList()
        error = null
        usedTfliteFallback = false
    }

    fun runTflite(bitmap: Bitmap): List<Pair<String, Float>> {
        if (interpreter == null || classNames.isEmpty()) return emptyList()
        val inputBuffer = preprocessImage(bitmap)
        val output = Array(1) { FloatArray(classNames.size) }
        interpreter.run(inputBuffer, output)
        return output[0]
            .withIndex()
            .sortedByDescending { it.value }
            .take(5)
            .map { idxVal -> classNames.getOrElse(idxVal.index) { "?" } to idxVal.value }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ИИ-анализатор растений", fontSize = 22.sp) },
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Галерея")
                }
                Button(
                    onClick = { takePictureLauncher.launch(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Камера")
                }
            }
            Spacer(Modifier.height(16.dp))
            imageUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val bitmap = uriToBitmap(context, uri)
                        if (bitmap == null) {
                            error = "Не удалось загрузить изображение"
                            return@Button
                        }
                        loading = true
                        error = null
                        analysis = null
                        rawAiResponse = null
                        tflitePredictions = emptyList()
                        usedTfliteFallback = false
                        statusMessage = null
                        scope.launch {
                            val base64 = uriToBase64(context, uri)
                            val result = StringBuilder()
                            var parsed: PlantAnalysisResult? = null
                            
                            if (base64 != null) {
                                val response = aiClient.sendMessage(
                                    history = emptyList(),
                                    userText = PlantAnalysisPrompt.TEXT,
                                    imageBase64 = base64,
                                    systemPrompt = "Ты — эксперт по растениям. Проанализируй изображение и определи название растения, состояние здоровья, проблемы, лечение и уход. Ответь на русском в JSON формате: {\"name\":\"название\",\"latinName\":\"лат название\",\"healthStatus\":\"состояние\",\"healthScore\":0-100,\"problems\":[],\"treatment\":[],\"care\":{\"watering\":\"\",\"light\":\"\",\"temperature\":\"\",\"fertilizer\":\"\"},\"facts\":[]}. Если называешь растение или болезнь — добавь в самом конце, отдельной строкой: KEYWORDS: название на английском"
                                )
                                if (response.isSuccess) {
                                    val text = response.getOrNull() ?: ""
                                    rawAiResponse = text
                                    parsed = parsePlantAnalysis(text)
                                }
                            }
                            
                            if (parsed != null) {
                                analysis = parsed
                                usedTfliteFallback = false
                            } else {
                                usedTfliteFallback = true
                                tflitePredictions = withContext(Dispatchers.Default) { runTflite(bitmap) }
                                if (tflitePredictions.isEmpty()) error = "Не удалось получить результат (ИИ прокси и локальная модель)"
                            }
                            loading = false
                        }
                    },
                    enabled = !loading
                ) { Text(if (loading) "Анализ…" else "Анализировать") }
            }
            Spacer(Modifier.height(16.dp))
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                analysis != null -> {
                    PlantAnalysisCard(analysis!!)
                    rawAiResponse?.let { raw ->
                        val index = raw.indexOf("🖼️")
                        if (index != -1) {
                            Spacer(Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "Эталонные изображения",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    AssistantMessageContent(
                                        text = raw.substring(index),
                                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                usedTfliteFallback && tflitePredictions.isNotEmpty() -> TfliteFallbackCard(tflitePredictions)
            }
        }
    }
}

private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    return BitmapFactory.decodeStream(inputStream)
}

private fun uriToBase64(context: Context, uri: Uri): String? {
    val bitmap = uriToBitmap(context, uri) ?: return null
    val maxSize = 1024
    val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
        val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun loadClassNames(context: Context, jsonFileName: String = "class_indices.json"): List<String> {
    val jsonString = context.assets.open(jsonFileName).bufferedReader().use { it.readText() }
    val json = org.json.JSONObject(jsonString)
    val keys = json.keys().asSequence().toList().sortedBy { it.toInt() }
    return keys.map { json.getString(it) }
}

private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(modelName)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
}

private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
    val inputImage = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
    val byteBuffer = ByteBuffer.allocateDirect(4 * 160 * 160 * 3)
    byteBuffer.order(ByteOrder.nativeOrder())
    for (y in 0 until 160) {
        for (x in 0 until 160) {
            val pixel = inputImage.getPixel(x, y)
            byteBuffer.putFloat(Color.red(pixel) / 255.0f)
            byteBuffer.putFloat(Color.green(pixel) / 255.0f)
            byteBuffer.putFloat(Color.blue(pixel) / 255.0f)
        }
    }
    byteBuffer.rewind()
    return byteBuffer
}

private fun parsePlantAnalysis(raw: String): PlantAnalysisResult? = try {
    var json = raw.trim()
    // Извлечь JSON из markdown ```json ... ``` (без substringAfter по "```" — иначе теряется тело)
    if (json.contains("```")) {
        json = json.substringAfter("```json", json).substringBefore("```").trim()
    }
    // Убрать префикс "json\n" если нет markdown
    if (!json.startsWith("{") && json.startsWith("json", ignoreCase = true)) {
        json = json.drop(4).trimStart()
    }
    if (!json.startsWith("{")) {
        val start = json.indexOf("{")
        val end = json.lastIndexOf("}")
        if (start >= 0 && end > start) json = json.substring(start, end + 1)
    }
    Gson().fromJson(json, PlantAnalysisResult::class.java)
} catch (_: Exception) { null }

@Composable
private fun TfliteFallbackCard(predictions: List<Pair<String, Float>>) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Локальная модель (TFLite)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("LM Studio недоступен или не вернул результат. Топ-5 по локальной модели:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            predictions.forEachIndexed { i, (label, prob) ->
                Text("${i + 1}. $label — ${(prob * 100).toInt()}%", Modifier.padding(vertical = 2.dp), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PlantAnalysisCard(a: PlantAnalysisResult) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(a.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (a.latinName.isNotBlank()) Text(a.latinName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        a.healthStatus.contains("здоров", ignoreCase = true) -> Icons.Filled.CheckCircle
                        a.healthStatus.contains("внимани", ignoreCase = true) -> Icons.Filled.Warning
                        else -> Icons.Filled.Error
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Состояние: ${a.healthStatus} (${a.healthScore}%)", style = MaterialTheme.typography.titleMedium)
            }
            if (a.problems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Проблемы:", fontWeight = FontWeight.SemiBold)
                a.problems.forEach { Text("• $it", Modifier.padding(vertical = 2.dp)) }
            }
            if (a.treatment.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Лечение / меры:", fontWeight = FontWeight.SemiBold)
                a.treatment.forEachIndexed { i, s -> Text("${i + 1}. $s", Modifier.padding(vertical = 2.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Уход:", fontWeight = FontWeight.SemiBold)
            if (a.care.watering.isNotBlank()) Text("Полив: ${a.care.watering}", Modifier.padding(vertical = 2.dp))
            if (a.care.light.isNotBlank()) Text("Свет: ${a.care.light}", Modifier.padding(vertical = 2.dp))
            if (a.care.temperature.isNotBlank()) Text("Температура: ${a.care.temperature}", Modifier.padding(vertical = 2.dp))
            if (a.care.fertilizer.isNotBlank()) Text("Удобрения: ${a.care.fertilizer}", Modifier.padding(vertical = 2.dp))
            if (a.facts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Факты:", fontWeight = FontWeight.SemiBold)
                a.facts.forEach { Text("• $it", Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}

private fun saveBitmapToUri(context: Context, bitmap: Bitmap): Uri {
    val cacheDir = context.cacheDir
    val file = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    return Uri.fromFile(file)
}
