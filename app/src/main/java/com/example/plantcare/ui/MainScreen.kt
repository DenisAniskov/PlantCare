package com.example.plantcare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantcare.data.Plant
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import com.example.plantcare.util.DiseaseJsonImporter
import com.example.plantcare.data.Disease
import androidx.compose.ui.platform.LocalContext
import com.example.plantcare.util.PlantJsonImporter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check

@Composable
fun MainScreen(
    plants: List<Plant>,
    onPlantClick: (Plant) -> Unit,
    onAddPlant: () -> Unit,
    onEditPlant: (Plant) -> Unit,
    onDeletePlant: (Plant) -> Unit,
    onShowReference: (() -> Unit)? = null,
    onShowWeather: (() -> Unit)? = null // теперь опционально
) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Мои растения",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp)
            )
                if (onShowReference != null) {
            Button(
                onClick = onShowReference,
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("Справочник", color = Color.White)
            }
        }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Кнопка '+ Добавить растение' удалена
                if (onShowWeather != null) {
        Button(
                        onClick = onShowWeather,
            modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) {
                        Text("Погода", fontSize = 20.sp, color = Color.White)
                    }
                }
        }
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(plants) { plant ->
                PlantListItem(
                    plant = plant,
                    onClick = { onPlantClick(plant) },
                    onEdit = { onEditPlant(plant) },
                    onDelete = { onDeletePlant(plant) }
                )
            }
            }
        }
        FloatingActionButton(
            onClick = onAddPlant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("+")
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlantListItem(
    plant: Plant,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Галочка/кружок слева (пример: всегда кружок, если будет статус - сделать как у NoteCard)
            /*
            IconButton(onClick = { /* TODO: mark as done/undone if нужно */ }) {
                if (plant.done) Text("✓", color = Color(0xFF388E3C), fontSize = 22.sp)
                else Text("○", color = Color.Gray, fontSize = 22.sp)
            }
            */
            // Для примера: просто кружок (можно добавить статус done у Plant, если потребуется)
            Text("○", color = Color.Gray, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plant.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = plant.type,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        }
    }
} 