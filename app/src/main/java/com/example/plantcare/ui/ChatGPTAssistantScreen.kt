package com.example.plantcare.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.plantcare.ai.AiClientProvider
import com.example.plantcare.sharedui.SharedChatAssistantScreen
import com.example.plantcare.ui.components.ProxyIndicator
import com.example.plantcare.ui.components.ProxyStatusBottomSheet
import java.io.ByteArrayOutputStream

private const val SYSTEM_PROMPT = "Ты — ассистент по уходу за растениями. Отвечай кратко, по делу на русском. Можно использовать списки и выделение.bold. Если называешь растение или болезнь — добавь в самом конце, отдельной строкой: KEYWORDS: название на английском"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGPTAssistantScreen(onBack: () -> Unit, aiService: com.example.plantcare.ai.AiService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val aiClient = remember { AiClientProvider.get(context) }
    
    // Set up status callback
    var statusMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(aiClient) {
        aiClient.setStatusCallback { status ->
            statusMessage = status
        }
    }

    var attachedBase64 by remember { mutableStateOf<String?>(null) }
    var showProxyDetails by remember { mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
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
            // Status message - shows real-time model status updates
            statusMessage?.let { status ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
             
            Box(modifier = Modifier.weight(1f)) {
                SharedChatAssistantScreen(
                    onSendLocal = { _ -> "" },
                    onSendRemote = { text, imageBase64, history ->
                        statusMessage = null
                        val formattedHistory = history.map { (role, content) ->
                            mapOf("role" to role, "content" to content)
                        }

                        var result = ""
                        try {
                            val response = aiClient.sendMessage(
                                history = formattedHistory,
                                userText = text,
                                imageBase64 = imageBase64,
                                systemPrompt = SYSTEM_PROMPT
                            )
                            result = if (response.isSuccess) {
                                statusMessage = null
                                response.getOrNull() ?: ""
                            } else {
                                "Ошибка: ${response.exceptionOrNull()?.message ?: "неизвестная ошибка"}"
                            }
                        } catch (e: Exception) {
                            result = "Ошибка: ${e.message}"
                        }
                        result
                    },
                    onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                    showTopBar = false,
                    attachedImageBase64 = attachedBase64,
                    onPickImage = { pickImageLauncher.launch("image/*") },
                    onClearAttached = { attachedBase64 = null },
                    onOpenHistory = { 
                        Toast.makeText(context, "История чатов скоро появится!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
