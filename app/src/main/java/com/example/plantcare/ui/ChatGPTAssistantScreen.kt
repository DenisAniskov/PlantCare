package com.example.plantcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import java.util.concurrent.TimeUnit
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGPTAssistantScreen(onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val welcomeMessage = "Здравствуйте! Я — ваш AI-ассистент по уходу за растениями. Задайте любой вопрос о комнатных или садовых растениях, и я помогу вам советом!"

    // --- История сообщений ---
    var messages by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var userInput by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // --- Для фото ---
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bmp = inputStream?.use { android.graphics.BitmapFactory.decodeStream(it) }
            imageBitmap = bmp
            // Конвертация в jpg и base64
            bmp?.let {
                val outputStream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                val byteArray = outputStream.toByteArray()
                imageBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            }
        } else {
            imageBitmap = null
            imageBase64 = null
        }
    }

    fun sendMessageToAI(message: String) {
        isLoading = true
        aiResponse = ""
        // Системный промпт (если нужен)
        val systemPrompt = mapOf(
            "role" to "system",
            "content" to "Ты — PlantCare Buddy, лучший друг и надежный эксперт по уходу за комнатными и садовыми растениями. ... (сюда весь текст промпта, как выше)"
        )
        // Формируем content для user: текст + фото (если есть)
        val userContent = mutableListOf<Map<String, Any>>()
        if (message.isNotBlank()) {
            userContent.add(mapOf("type" to "text", "text" to message))
        }
        imageBase64?.let { base64 ->
            userContent.add(mapOf(
                "type" to "image_url",
                "image_url" to mapOf("url" to "data:image/jpeg;base64,$base64")
            ))
        }
        // История: системный промпт + последние сообщения
        val history = if (messages.isEmpty() || messages.firstOrNull()?.get("role") != "system") {
            listOf(systemPrompt) + listOf(mapOf("role" to "user", "content" to userContent))
        } else {
            messages.takeLast(10) + mapOf("role" to "user", "content" to userContent)
        }
        messages = history
        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .writeTimeout(300, TimeUnit.SECONDS)
                    .build()
                val mediaType = "application/json".toMediaType()
                val requestBody = org.json.JSONObject(mapOf(
                    "model" to "google/gemma-3-12b",
                    "messages" to history
                )).toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("http://185.108.208.121:1234/v1/chat/completions")
                    .addHeader("Authorization", "Bearer local-key")
                    .post(requestBody)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.charStream()?.readText()
                Log.d("AI_DEBUG", "Ответ: $responseBody")
                val json = org.json.JSONObject(responseBody)
                if (json.has("choices")) {
                    val content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    aiResponse = content.trim()
                    // Добавить ответ ассистента в историю
                    messages = history + mapOf("role" to "assistant", "content" to aiResponse)
                } else if (json.has("error")) {
                    val msg = json.getJSONObject("error").optString("message", responseBody ?: "Unknown error")
                    aiResponse = "Ошибка API: $msg"
                } else {
                    aiResponse = "Неожиданный ответ: " + (responseBody ?: "null")
                }
            } catch (e: Exception) {
                aiResponse = "Ошибка: ${e.localizedMessage}"
            } finally {
                isLoading = false
                // После отправки сбрасываем фото
                imageUri = null
                imageBitmap = null
                imageBase64 = null
            }
        }
    }

    fun resetMemory() {
        messages = listOf()
        aiResponse = ""
        imageUri = null
        imageBitmap = null
        imageBase64 = null
    }

    // Сброс памяти при выходе
    DisposableEffect(Unit) {
        onDispose {
            resetMemory()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("AI-ассистент") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (userInput.isEmpty()) {
                                    Text(
                                        text = "Введите ваш вопрос...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { pickImageLauncher.launch("image/*") }, enabled = !isLoading) {
                        Text(if (imageBitmap == null) "📎 Фото" else "Заменить")
                    }
                    if (imageBitmap != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(onClick = {
                            imageUri = null
                            imageBitmap = null
                            imageBase64 = null
                        }, enabled = !isLoading) {
                            Text("Удалить")
                        }
                    }
                }
                if (imageBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(bitmap = imageBitmap!!.asImageBitmap(), contentDescription = "Выбранное фото", modifier = Modifier.height(120.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { if (userInput.isNotBlank() || imageBase64 != null) sendMessageToAI(userInput) },
                        enabled = !isLoading && (userInput.isNotBlank() || imageBase64 != null)
                    ) {
                        Text(if (isLoading) "Отправка..." else "Отправить")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { resetMemory() },
                        enabled = !isLoading
                    ) {
                        Text("Очистить память")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        aiResponse.isNotBlank() -> {
                            Column {
                                Text("Ответ:", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(aiResponse, style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    clipboardManager.setText(AnnotatedString(aiResponse))
                                }) {
                                    Text("Скопировать ответ")
                                }
                            }
                        }
                        else -> {
                            Text(welcomeMessage, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
} 