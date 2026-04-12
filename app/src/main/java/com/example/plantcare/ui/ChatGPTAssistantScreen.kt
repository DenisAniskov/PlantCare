package com.example.plantcare.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plantcare.ai.AiClientProvider
import com.example.plantcare.data.ChatMessageEntity
import com.example.plantcare.data.ChatSession
import com.example.plantcare.sharedui.ChatMessage
import com.example.plantcare.sharedui.ChatRole
import com.example.plantcare.sharedui.SharedChatAssistantScreen
import com.example.plantcare.ui.components.ProxyIndicator
import com.example.plantcare.ui.components.ProxyStatusBottomSheet
import com.example.plantcare.viewmodel.PlantCareViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private const val SYSTEM_PROMPT = "Ты — ассистент по уходу за растениями. Отвечай кратко, по делу на русском. Можно использовать списки и выделение.bold. Если называешь растение или болезнь — добавь в самом конце, отдельной строкой: KEYWORDS: название на английском"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGPTAssistantScreen(
    onBack: () -> Unit,
    aiService: com.example.plantcare.ai.AiService,
    viewModel: PlantCareViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val aiClient = remember { AiClientProvider.get(context) }
    
    val chatSessions by viewModel.chatSessions.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var newTitleText by remember { mutableStateOf("") }
    
    LaunchedEffect(aiClient) {
        aiClient.setStatusCallback { status ->
            statusMessage = status
        }
    }

    // Инициализация сессии
    LaunchedEffect(chatSessions) {
        if (currentSessionId == null) {
            if (chatSessions.isNotEmpty()) {
                viewModel.selectChatSession(chatSessions.first().id)
            } else {
                viewModel.startNewChat()
            }
        }
    }

    // Авто-скролл вниз при новых сообщениях
    val listState = rememberLazyListState()
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size)
        }
    }

    var attachedBase64 by remember { mutableStateOf<String?>(null) }
    var showProxyDetails by remember { mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("История чатов") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(chatSessions) { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectChatSession(session.id)
                                    showHistoryDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(session.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt))
                                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { 
                                sessionToRename = session
                                newTitleText = session.title
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Переименовать")
                            }
                            IconButton(onClick = { viewModel.deleteChatSession(session) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (sessionToRename != null) {
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("Переименовать чат") },
            text = {
                TextField(
                    value = newTitleText,
                    onValueChange = { newTitleText = it },
                    label = { Text("Название") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    sessionToRename?.let { viewModel.renameChatSession(it.id, newTitleText) }
                    sessionToRename = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) { Text("Отмена") }
            }
        )
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val out = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            attachedBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: return@rememberLauncherForActivityResult
        val maxSize = 1024
        val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        attachedBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ИИ-ассистент") },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            statusMessage?.let { status ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(text = status, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
             
            Box(modifier = Modifier.weight(1f)) {
                val displayMessages = remember(currentMessages) {
                    if (currentMessages.isEmpty()) {
                        listOf(ChatMessage(
                            role = ChatRole.SYSTEM,
                            text = "Здравствуйте! Я — ваш ассистент по уходу за растениями. Задайте вопрос или приложите фото."
                        ))
                    } else {
                        currentMessages.map { 
                            ChatMessage(
                                role = when(it.role) {
                                    "user" -> ChatRole.USER
                                    "assistant" -> ChatRole.ASSISTANT
                                    else -> ChatRole.SYSTEM
                                },
                                text = it.content
                            )
                        }
                    }
                }

                SharedChatAssistantScreen(
                    onSendLocal = { _ -> "" },
                    onSendRemote = { text, imageBase64, history, onChunk ->
                        statusMessage = null
                        if (currentSessionId == null) {
                            return@SharedChatAssistantScreen "Ошибка: Сессия чата не инициализирована."
                        }

                        val userDisplayContent = if (!imageBase64.isNullOrBlank()) {
                            "{фото прикреплено} ${text ?: ""}".trim()
                        } else {
                            text ?: ""
                        }

                        viewModel.saveChatMessage("user", userDisplayContent)
                        
                        var result = ""
                        try {
                            val response = aiClient.sendMessageStreaming(
                                history = history.map { mapOf("role" to it.first, "content" to it.second) },
                                userText = text,
                                imageBase64 = imageBase64,
                                systemPrompt = SYSTEM_PROMPT,
                                onChunk = { chunk ->
                                    result = chunk
                                    onChunk(chunk)
                                }
                            )
                            if (response.isSuccess) {
                                result = response.getOrNull() ?: ""
                                viewModel.saveChatMessage("assistant", result)
                            } else {
                                result = "Ошибка: ${response.exceptionOrNull()?.message}"
                                viewModel.saveChatMessage("assistant", result)
                            }
                        } catch (e: Exception) {
                            result = "Ошибка: ${e.message}"
                            viewModel.saveChatMessage("assistant", result)
                        }
                        result
                    },
                    onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                    showTopBar = false,
                    attachedImageBase64 = attachedBase64,
                    onPickImage = { pickImageLauncher.launch("image/*") },
                    onTakePicture = { takePictureLauncher.launch(null) },
                    onClearAttached = { attachedBase64 = null },
                    onOpenHistory = { showHistoryDialog = true },
                    onNewChat = { viewModel.startNewChat() },
                    modifier = Modifier.fillMaxSize(),
                    externalMessages = displayMessages
                )
            }
        }
    }
}
