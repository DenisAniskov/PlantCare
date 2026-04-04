package com.example.plantcare.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.plantcare.core.LocalRagEngine
import java.io.BufferedReader

private fun readResourceText(path: String): String =
    object {}.javaClass.classLoader.getResourceAsStream(path)?.use { it.bufferedReader().use(BufferedReader::readText) } ?: "[]"

@Composable
@Preview
fun App() {
    val engine by remember {
        mutableStateOf(
            LocalRagEngine(
                plantsJson = readResourceText("assets/plants.json"),
                pestsJson = readResourceText("assets/pests.json"),
                diseasesJson = readResourceText("assets/diseases.json"),
                tipsJson = readResourceText("assets/plant_care_tips.json"),
            )
        )
    }

    var query by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("Введите запрос и нажмите Поиск") }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PlantCare Desktop — Справочник")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Что ищем (например: гортензия, фитофтороз, паутинный клещ)") }
                )
                Button(onClick = {
                    val found = engine.fetchEntry(query)
                    output = if (found != null) {
                        val (title, text) = found
                        "[Справочник: автоматический поиск]\nНазвание: $title\n$text"
                    } else {
                        "[Справочник] Нет записи для: \"$query\". Попробуйте другое ключевое слово."
                    }
                }) {
                    Text("Поиск")
                }
            }
            Text(output)
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "PlantCare") {
        App()
    }
}
