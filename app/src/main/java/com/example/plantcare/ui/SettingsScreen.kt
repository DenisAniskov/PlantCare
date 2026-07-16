package com.example.plantcare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.plantcare.sharedui.PlantCareDesign
import com.example.plantcare.util.Prefs

/**
 * Экран настроек PlantCare: темы, прокси, AI backend, ключи API.
 * Все значения читаются из SharedPreferences один раз и пишутся туда же при изменении.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    darkTheme: Boolean,
    onSetDarkTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current

    var proxyUrl by remember { mutableStateOf(TextFieldValue(Prefs.getProxyBaseUrl(context))) }
    var openRouterKey by remember { mutableStateOf(TextFieldValue(Prefs.getOpenRouterApiKey(context))) }
    var showOpenRouterKey by rememberSaveable { mutableStateOf(false) }
    var lmStudioUrl by remember { mutableStateOf(TextFieldValue(Prefs.getLmStudioBaseUrl(context))) }
    var backend by remember { mutableStateOf(Prefs.getBackend(context)) }
    var groqKey by remember { mutableStateOf(TextFieldValue(Prefs.getGroqApiKey(context))) }
    var showGroqKey by rememberSaveable { mutableStateOf(false) }
    var groqModel by remember { mutableStateOf(TextFieldValue(Prefs.getGroqModel(context))) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Внешний вид
            SettingsCard("🎨 Внешний вид") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Тёмная тема", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Адаптивные цвета Material Design 3",
                            fontSize = 12.sp,
                            color = PlantCareDesign.Colors.TextSecondary
                        )
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = { v ->
                            onSetDarkTheme(v)
                            Prefs.setDarkTheme(context, v)
                        }
                    )
                }
            }

            // Прокси
            SettingsCard("🌐 Прокси-соединение") {
                OutlinedTextField(
                    value = proxyUrl,
                    onValueChange = {
                        proxyUrl = it
                        if (it.text.isNotBlank()) Prefs.setProxyBaseUrl(context, it.text)
                    },
                    label = { Text("Базовый URL прокси") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Индикатор статуса прокси виден на главном экране. При сбое включается офлайн-режим.",
                    fontSize = 12.sp,
                    color = PlantCareDesign.Colors.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // AI backend
            SettingsCard("🤖 AI backend") {
                Backends.forEach { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = backend.equals(b, ignoreCase = true),
                            onClick = {
                                backend = b
                                Prefs.setBackend(context, b)
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(b)
                    }
                }
            }

            // OpenRouter
            SettingsCard("🔗 OpenRouter") {
                OutlinedTextField(
                    value = openRouterKey,
                    onValueChange = {
                        openRouterKey = it
                        Prefs.setOpenRouterApiKey(context, it.text)
                    },
                    label = { Text("OpenRouter API key") },
                    singleLine = true,
                    visualTransformation = if (showOpenRouterKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOpenRouterKey = !showOpenRouterKey }) {
                            Icon(
                                if (showOpenRouterKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showOpenRouterKey) "Скрыть" else "Показать"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Пустой ключ → приложение использует резервный каскад и офлайн-базу.",
                    fontSize = 12.sp,
                    color = PlantCareDesign.Colors.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // LM Studio
            SettingsCard("🏠 LM Studio (локальный)") {
                OutlinedTextField(
                    value = lmStudioUrl,
                    onValueChange = {
                        lmStudioUrl = it
                        if (it.text.isNotBlank()) Prefs.setLmStudioBaseUrl(context, it.text)
                    },
                    label = { Text("Базовый URL LM Studio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Groq
            SettingsCard("⚡ Groq") {
                OutlinedTextField(
                    value = groqKey,
                    onValueChange = {
                        groqKey = it
                        if (it.text.isNotBlank()) Prefs.setGroqApiKey(context, it.text)
                    },
                    label = { Text("Groq API key") },
                    singleLine = true,
                    visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGroqKey = !showGroqKey }) {
                            Icon(
                                if (showGroqKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showGroqKey) "Скрыть" else "Показать"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = groqModel,
                    onValueChange = {
                        groqModel = it
                        if (it.text.isNotBlank()) Prefs.setGroqModel(context, it.text)
                    },
                    label = { Text("Model name (например llama-3.1-8b-instant)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                "ℹ️ Ключи хранятся только на устройстве (SharedPreferences) и никуда не отправляются, кроме как в API выбранного провайдера.",
                fontSize = 12.sp,
                color = PlantCareDesign.Colors.TextTertiary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}

private val Backends = listOf("GROQ", "OPENROUTER", "LM_STUDIO")