package com.example.plantcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantcare.data.Plant
import com.example.plantcare.data.ReferencePlant

@Composable
fun AddEditPlantDialog(
    initialPlant: Plant? = null,
    onDismiss: () -> Unit,
    onSave: (Plant) -> Unit,
    referencePlants: List<ReferencePlant> = emptyList()
) {
    var name by remember { mutableStateOf(TextFieldValue(initialPlant?.name ?: "")) }
    var type by remember { mutableStateOf(TextFieldValue(initialPlant?.type ?: "")) }
    var notes by remember { mutableStateOf(TextFieldValue(initialPlant?.notes ?: "")) }
    var showReferenceList by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPlant == null) "Добавить растение" else "Редактировать растение",
                fontSize = 22.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp)
                )
                if (referencePlants.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showReferenceList = true }) {
                            Text("Выбрать из справочника")
                        }
                    }
                }
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Тип") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp)
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.text.isNotBlank() && type.text.isNotBlank()) {
                        onSave(
                            Plant(
                                id = initialPlant?.id ?: 0,
                                name = name.text,
                                type = type.text,
                                notes = notes.text
                            )
                        )
                    }
                },
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Сохранить", fontSize = 18.sp)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(8.dp)) {
                Text("Отмена", fontSize = 18.sp)
            }
        }
    )

    if (showReferenceList) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(referencePlants, query) {
            val q = query.trim().lowercase()
            if (q.isBlank()) referencePlants
            else referencePlants.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
        }
        AlertDialog(
            onDismissRequest = { showReferenceList = false },
            title = { Text("Справочник растений") },
            confirmButton = { TextButton(onClick = { showReferenceList = false }) { Text("Закрыть") } },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Поиск") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(filtered.take(100)) { plant ->
                            ListItem(
                                headlineContent = { Text(plant.name) },
                                supportingContent = { Text(plant.description.take(120) + if (plant.description.length > 120) "…" else "") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                trailingContent = {
                                    TextButton(onClick = {
                                        name = TextFieldValue(plant.name)
                                        if (notes.text.isBlank()) notes = TextFieldValue(plant.description)
                                        showReferenceList = false
                                    }) { Text("Выбрать") }
                                }
                            )
                        }
                    }
                }
            }
        )
    }
} 