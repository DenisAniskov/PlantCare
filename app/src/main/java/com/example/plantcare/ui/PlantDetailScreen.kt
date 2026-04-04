package com.example.plantcare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantcare.data.Plant
import com.example.plantcare.data.CareEvent
import com.example.plantcare.data.CareEventType
import com.example.plantcare.ui.components.ProxyIndicator
import com.example.plantcare.ui.components.ProxyStatusBottomSheet
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.style.TextDecoration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    plant: Plant,
    events: List<CareEvent>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onAddEvent: () -> Unit,
    onMarkEventDone: (CareEvent) -> Unit,
    onEditEvent: (CareEvent) -> Unit,
    onDeleteEvent: (CareEvent) -> Unit
) {
    var showProxyDetails by remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plant.name, fontSize = 22.sp) },
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
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .padding(padding)
        ) {
            Text(
                text = "Тип: ${plant.type}",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
            if (plant.notes.isNotBlank()) {
                Text(
                    text = "Заметки: ${plant.notes}",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddEvent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("+ Добавить событие ухода", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "События ухода:",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                items(events, key = { it.id }, contentType = { "CareEvent" }) { event ->
                    CareEventListItem(
                        event = event,
                        onMarkDone = { onMarkEventDone(event) },
                        onEdit = { onEditEvent(event) },
                        onDelete = { onDeleteEvent(event) }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CareEventListItem(
    event: CareEvent,
    onMarkDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeLabel = when (event.type) {
        CareEventType.WATERING -> "Полив 🌊"
        CareEventType.FERTILIZING -> "Подкормка 🌱"
        CareEventType.SPRAYING -> "Опрыскивание 💦"
        CareEventType.REPOTTING -> "Пересадка 🏺"
    }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val dateTimeFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val lastDateStr = remember(event.lastDate) {
        event.lastDate?.let {
            val cal = Calendar.getInstance().apply { timeInMillis = it }
            if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0) {
                dateFormat.format(Date(it))
            } else {
                dateTimeFormat.format(Date(it))
            }
        } ?: "—"
    }
    val nextDateStr = remember(event.nextDate) {
        event.nextDate?.let { dateFormat.format(Date(it)) } ?: "—"
    }
    val intervalStr = remember(event.intervalDays) {
        val interval = event.intervalDays ?: return@remember null
        if (interval >= 1f) {
            "Интервал: ${interval.toInt()} дней"
        } else {
            val totalMinutes = (interval * 24 * 60).toInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            buildString {
                append("Интервал: ")
                if (hours > 0) append("$hours ч ")
                if (minutes > 0) append("$minutes мин")
                if (hours == 0 && minutes == 0) append("<1 мин")
            }.trim()
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кружок/галочка слева
            IconButton(onClick = onMarkDone, enabled = !event.done, modifier = Modifier.padding(end = 8.dp)) {
                if (event.done) Text("✓", color = Color(0xFF388E3C), fontSize = 22.sp)
                else Text("○", color = Color.Gray, fontSize = 22.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    typeLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (event.done) Color(0xFF388E3C) else Color.Unspecified,
                    textDecoration = if (event.done) TextDecoration.LineThrough else TextDecoration.None
                )
                if (event.type == CareEventType.FERTILIZING && !event.fertilizerType.isNullOrBlank()) {
                    Text("Удобрение: ${event.fertilizerType}", fontSize = 16.sp)
                }
                if (event.intervalDays != null) {
                    intervalStr?.let {
                        Text(it, fontSize = 16.sp)
                }
                }
                Text(
                    "Последнее выполнение: $lastDateStr",
                    fontSize = 16.sp,
                    color = if (event.done) Color(0xFF388E3C) else Color.Unspecified,
                    textDecoration = if (event.done) TextDecoration.LineThrough else TextDecoration.None
                )
                if (event.type == CareEventType.REPOTTING) {
                    Text("Следующая пересадка: $nextDateStr", fontSize = 16.sp)
                }
            }
            // Кнопки редактирования и удаления
            IconButton(onClick = onEdit, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                }
            IconButton(onClick = onDelete, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        }
    }
} 