package com.example.plantcare.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import kotlinx.coroutines.launch

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(val role: ChatRole, val text: String)

@Composable
fun AssistantMessageContent(text: String, textColor: androidx.compose.ui.graphics.Color) {
    val uriHandler = LocalUriHandler.current
    val context = LocalPlatformContext.current
    
    // Разделяем рассуждения и основной текст
    val thinkingMatch = Regex("<thinking>(.*?)</thinking>", RegexOption.DOT_MATCHES_ALL).find(text)
    val thinkingText = thinkingMatch?.groupValues?.get(1)?.trim()
    val mainText = text.replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "").trim()
    
    val lines = mainText.split("\n")
    
    Column {
        if (!thinkingText.isNullOrBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = { expanded = !expanded }
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (expanded) "Скрыть рассуждения" else "Посмотреть ход мыслей...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = thinkingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }

        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                // Matches "🔍 http..." or "🔍  http..." etc.
                trimmed.startsWith("🔍") && trimmed.contains("http") -> {
                    // Extract URL more carefully: take only the first word after the emoji
                    val rawUrl = trimmed.substringAfter("🔍").trim()
                    // More aggressive URL cleaning for Markdown-like formats or trailing brackets
                    val url = rawUrl.split(Regex("\\s+")).firstOrNull()
                        ?.removePrefix("(")?.removeSuffix(")")
                        ?.removePrefix("[")?.removeSuffix("]") ?: ""
                    
                    if (url.startsWith("http")) {
                        Spacer(Modifier.height(8.dp))
                        var errorMessage by remember { mutableStateOf<String?>(null) }
                        
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            val request = remember(url) {
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .httpHeaders(NetworkHeaders.Builder()
                                        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                        .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                                        .set("Accept-Language", "en-US,en;q=0.9")
                                        .set("Cache-Control", "no-cache")
                                        .build())
                                    .crossfade(true)
                                    .build()
                            }
                            SubcomposeAsyncImage(
                                model = request,
                                contentDescription = "Plant reference image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 500.dp),
                                contentScale = ContentScale.FillWidth,
                                loading = {
                                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                                    }
                                },
                                onError = { state ->
                                    val err = state.result.throwable
                                    errorMessage = err.message ?: err.toString()
                                    println("CoilError: Failed to load $url - $err")
                                },
                                error = {
                                    Column(
                                        Modifier.fillMaxSize().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("⚠️", fontSize = 24.sp)
                                        Text("Не удалось загрузить фото", style = MaterialTheme.typography.labelSmall)
                                        if (errorMessage != null) {
                                            Text(
                                                text = errorMessage!!,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                trimmed.startsWith("📎") && trimmed.contains("http") -> {
                    val url = trimmed.substringAfter("📎").substringBefore(" (").trim()
                    if (url.startsWith("http")) {
                        TextButton(
                            onClick = { try { uriHandler.openUri(url) } catch(e: Exception) {} },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                else -> {
                    if (line.isNotBlank()) {
                        androidx.compose.foundation.text.BasicText(
                            text = parseMarkdownToAnnotatedString(line),
                            style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedChatAssistantScreen(
    onSendLocal: suspend (String) -> String,
    onSendRemote: suspend (String?, String?, List<Pair<String, String>>, onChunk: (String) -> Unit) -> String,
    onCopy: (String) -> Unit,
    showTopBar: Boolean = true,
    title: String = "ИИ-ассистент",
    attachedImageBase64: String? = null,
    onPickImage: () -> Unit = {},
    onTakePicture: () -> Unit = {},
    onClearAttached: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onNewChat: () -> Unit = {},
    externalMessages: List<ChatMessage>? = null,
    modifier: Modifier = Modifier
) {
    var userInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // Use external messages if provided, otherwise fallback to local state
    var localMessages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    text = "Здравствуйте! Я — ваш ассистент по уходу за растениями. Задайте вопрос или приложите фото."
                )
            )
        )
    }
    
    val currentMessages = externalMessages ?: localMessages
    var streamingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    val displayList = remember(currentMessages, streamingMessage) {
        if (streamingMessage != null) currentMessages + streamingMessage!! else currentMessages
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun buildHistory(): List<Pair<String, String>> = currentMessages
        .filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
        .map { msg ->
            val role = when (msg.role) {
                ChatRole.USER -> "user"
                ChatRole.ASSISTANT -> "assistant"
                ChatRole.SYSTEM -> "system"
            }
            role to msg.text
        }

    Column(modifier = modifier.fillMaxSize()) {
        if (showTopBar) {
            TopAppBar(title = { Text(title) })
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onOpenHistory,
                enabled = !isLoading
            ) { Text("📋 История") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    if (externalMessages != null) {
                        onNewChat()
                    } else {
                        localMessages = listOf(ChatMessage(ChatRole.SYSTEM, text = "Начат новый чат. Задайте вопрос или приложите фото."))
                    }
                },
                enabled = !isLoading
            ) { Text("➕ Новый чат") }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(currentMessages.size, streamingMessage?.text) {
            if (displayList.isNotEmpty()) {
                listState.animateScrollToItem(displayList.size - 1)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            state = listState
        ) {
            items(items = displayList) { msg ->
                val isUser = msg.role == ChatRole.USER
                val bubbleColor = when (msg.role) {
                    ChatRole.USER -> MaterialTheme.colorScheme.primaryContainer
                    ChatRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
                    ChatRole.SYSTEM -> MaterialTheme.colorScheme.surface
                }
                val textColor = when (msg.role) {
                    ChatRole.USER -> MaterialTheme.colorScheme.onPrimaryContainer
                    ChatRole.ASSISTANT -> MaterialTheme.colorScheme.onSurfaceVariant
                    ChatRole.SYSTEM -> MaterialTheme.colorScheme.onSurface
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(color = bubbleColor, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(12.dp).widthIn(max = 520.dp)) {
                            val label = when (msg.role) {
                                ChatRole.USER -> "Вы"
                                ChatRole.ASSISTANT -> "Ассистент"
                                ChatRole.SYSTEM -> "Подсказка"
                            }
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor.copy(alpha = 0.8f))
                            Spacer(Modifier.height(4.dp))
                            if (msg.role == ChatRole.ASSISTANT) {
                                AssistantMessageContent(msg.text, textColor)
                            } else {
                                Text(msg.text, color = textColor)
                            }
                            if (msg.role == ChatRole.ASSISTANT) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onCopy(msg.text) }) { Text("Копировать") }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (attachedImageBase64 != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("Фото прикреплено", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onClearAttached) { Text("Убрать") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onTakePicture,
                        enabled = !isLoading,
                        modifier = Modifier.heightIn(min = 56.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("📷")
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = onPickImage,
                        enabled = !isLoading,
                        modifier = Modifier.heightIn(min = 56.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("🖼️")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 4,
                        enabled = !isLoading,
                        label = { Text("Введите вопрос…") }
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val text = userInput.trim()
                            if (text.isBlank() && attachedImageBase64.isNullOrBlank()) return@Button
                            val history = buildHistory()
                            val userMsg = if (text.isNotBlank()) text else "[Фото растения]"

                            // Если сообщения внешние, мы просто уведомляем о новом сообщении (через колбэк отправки)
                            // Если локальные - добавляем в список сами
                            if (externalMessages == null) {
                                localMessages += ChatMessage(ChatRole.USER, userMsg)
                            }

                            userInput = ""
                            isLoading = true
                            val img = attachedImageBase64
                            onClearAttached()

                            scope.launch {
                                val result = runCatching {
                                    onSendRemote(text.takeIf { it.isNotBlank() }, img, history) { chunk ->
                                        // chunk contains the full current accumulated text
                                        streamingMessage = ChatMessage(ChatRole.ASSISTANT, chunk)
                                    }
                                }
                                val finalReply = result.getOrElse { "Ошибка: ${it.message}" }
                                streamingMessage = null
                                if (externalMessages == null) {
                                    localMessages = localMessages + ChatMessage(ChatRole.ASSISTANT, finalReply)
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && (userInput.isNotBlank() || attachedImageBase64 != null)
                    ) { Text(if (isLoading) "…" else "Отправить") }
                }
            }
        }
    }
}
