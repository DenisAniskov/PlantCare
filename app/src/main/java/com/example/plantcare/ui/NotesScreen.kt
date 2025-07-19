package com.example.plantcare.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import com.example.plantcare.viewmodel.PlantCareViewModel
import com.example.plantcare.data.Note
import com.example.plantcare.data.Plant
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material.icons.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onBack: () -> Unit, viewModel: PlantCareViewModel) {
    val notes by viewModel.notes.collectAsState()
    val plants by viewModel.plants.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заметки", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            if (notes.isEmpty()) {
                Text("Нет заметок", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                notes.sortedByDescending { it.date }.forEach { note ->
                    NoteCard(
                        note = note,
                        plant = plants.find { it.id == note.plantId },
                        onEdit = { editNote = note; showDialog = true },
                        onDelete = { viewModel.deleteNote(note) },
                        onToggleDone = { viewModel.toggleNoteDone(note) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        if (showDialog) {
            NoteDialog(
                plants = plants,
                initialNote = editNote,
                onDismiss = { showDialog = false; editNote = null },
                onSave = { note ->
                    if (editNote == null) viewModel.addNote(note) else viewModel.updateNote(note)
                    showDialog = false; editNote = null
                }
            )
        }
    }
}

@Composable
fun NoteCard(note: Note, plant: Plant?, onEdit: () -> Unit, onDelete: () -> Unit, onToggleDone: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(note.date)), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleDone) {
                    if (note.done) Text("✓", color = Color(0xFF388E3C), fontSize = 22.sp)
                    else Text("○", color = Color.Gray, fontSize = 22.sp)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Редактировать") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Удалить") }
            }
            if (plant != null) {
                Text("Растение: ${plant.name}", fontSize = 16.sp, color = Color(0xFF388E3C))
            }
            Text(
                note.text,
                fontSize = 18.sp,
                color = if (note.done) Color(0xFF388E3C) else Color.Unspecified,
                style = if (note.done) LocalTextStyle.current.copy(textDecoration = TextDecoration.LineThrough) else LocalTextStyle.current
            )
        }
    }
}

@Composable
fun NoteDialog(
    plants: List<Plant>,
    initialNote: Note?,
    onDismiss: () -> Unit,
    onSave: (Note) -> Unit
) {
    var text by remember { mutableStateOf(initialNote?.text ?: "") }
    var attachToPlant by remember { mutableStateOf(initialNote?.plantId != null) }
    var selectedPlantId by remember { mutableStateOf(initialNote?.plantId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialNote == null) "Новая заметка" else "Редактировать заметку") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст заметки") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = attachToPlant, onCheckedChange = { attachToPlant = it })
                    Text("Привязать к растению")
                }
                if (attachToPlant) {
                    DropdownMenuPlantSelector(
                        plants = plants,
                        selectedPlantId = selectedPlantId,
                        onSelect = { selectedPlantId = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSave(
                            Note(
                                id = initialNote?.id ?: 0,
                                text = text,
                                date = initialNote?.date ?: System.currentTimeMillis(),
                                plantId = if (attachToPlant) selectedPlantId else null
                            )
                        )
                    }
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun DropdownMenuPlantSelector(plants: List<Plant>, selectedPlantId: Int?, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlant = plants.find { it.id == selectedPlantId }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text(selectedPlant?.name ?: "Выберите растение")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        plants.forEach { plant ->
            DropdownMenuItem(
                text = { Text(plant.name) },
                onClick = {
                    onSelect(plant.id)
                    expanded = false
                }
            )
        }
    }
} 