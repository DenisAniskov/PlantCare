package com.example.plantcare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.example.plantcare.data.CareEvent
import com.example.plantcare.data.CareEventType
import com.example.plantcare.sharedui.PlantCareDesign
import com.example.plantcare.viewmodel.PlantCareViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Дашборд «Сегодня»: агрегированный вид задач по уходу для всех растений.
 * Показывает просроченные и предстоящие события, вдохновляющую статистику.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: PlantCareViewModel,
    onBack: () -> Unit
) {
    val events by viewModel.allEvents.collectAsState()
    val plants by viewModel.plants.collectAsState()

    val today = remember { Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis }
    val dayMs = 86_400_000L
    val in7Days = today + 7 * dayMs

    val plantNameById: (Int) -> String = { id -> plants.firstOrNull { it.id == id }?.name ?: "Растение" }

    val overdue = remember(events, today) {
        events.map { e -> Pair(e, dueAt(e, today, dayMs)) }
            .filter { it.first.done.not() && it.second != null && it.second!! <= today }
            .sortedBy { it.second }
    }
    val upcoming = remember(events, today, in7Days) {
        events.map { e -> Pair(e, dueAt(e, today, dayMs)) }
            .filter { it.first.done.not() && it.second != null && it.second!! in (today + 1)..in7Days }
            .sortedBy { it.second }
    }
    val doneToday = remember(events, today) {
        events.count { it.done && it.lastDate != null && it.lastDate!! >= today }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сегодня", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                SummaryRow(totalCount = events.count { !it.done }, overdueCount = overdue.size, doneToday = doneToday)
            }
            item { SectionHeader("⏰ Просроченные (${overdue.size})", PlantCareDesign.Colors.Error) }
            if (overdue.isEmpty()) {
                item { EmptyHint("Просроченных задач нет. Отличная работа! 🎉", Icons.Filled.CheckCircle, PlantCareDesign.Colors.Success) }
            } else {
                items(overdue, key = { "o-${it.first.id}" }) { (e, due) ->
                    TodayCard(e, plantNameById(e.plantId), due = due, isOverdue = true, onMarkDone = {})
                }
            }
            item { SectionHeader("📅 Скоро (7 дней, ${upcoming.size})", PlantCareDesign.Colors.Info) }
            if (upcoming.isEmpty()) {
                item { EmptyHint("На неделю нет запланированных задач. Отдыхаем 🌱", Icons.Filled.Schedule, PlantCareDesign.Colors.TextTertiary) }
            } else {
                items(upcoming, key = { "u-${it.first.id}" }) { (e, due) ->
                    TodayCard(e, plantNameById(e.plantId), due = due, isOverdue = false, onMarkDone = {})
                }
            }
            if (overdue.isEmpty() && upcoming.isEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    EmptyHint("Задач по уходу пока нет. Добавьте растения и события ухода, чтобы увидеть здесь напоминания.", Icons.Filled.Grass, PlantCareDesign.Colors.Primary)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String, accent: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = accent,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SummaryRow(totalCount: Int, overdueCount: Int, doneToday: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryCard("Активных", totalCount.toString(), PlantCareDesign.Colors.Primary, Modifier.weight(1f))
        SummaryCard("Просрочено", overdueCount.toString(), PlantCareDesign.Colors.Error, Modifier.weight(1f))
        SummaryCard("Сегодня ✓", doneToday.toString(), PlantCareDesign.Colors.Success, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 12.sp, color = PlantCareDesign.Colors.TextSecondary)
        }
    }
}

@Composable
private fun EmptyHint(text: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodayCard(
    event: CareEvent,
    plantName: String,
    due: Long?,
    isOverdue: Boolean,
    onMarkDone: () -> Unit
) {
    val accent = if (isOverdue) PlantCareDesign.Colors.Error else PlantCareDesign.Colors.Info
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val (icon, typeLabel) = when (event.type) {
        CareEventType.WATERING -> Icons.Filled.WaterDrop to "Полив"
        CareEventType.FERTILIZING -> Icons.Filled.LocalFlorist to "Подкормка"
        CareEventType.SPRAYING -> Icons.Filled.InvertColors to "Опрыскивание"
        CareEventType.REPOTTING -> Icons.Filled.Grass to "Пересадка"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$typeLabel · $plantName", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val dueText = if (due != null) {
                    if (isOverdue) "Просрочено: ${dateFmt.format(Date(due))}"
                    else "До: ${dateFmt.format(Date(due))}"
                } else {
                    "Дата не задана"
                }
                Text(dueText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (isOverdue) Icons.Filled.WarningAmber else Icons.Filled.Schedule,
                contentDescription = if (isOverdue) "Просрочено" else "Скоро",
                tint = accent
            )
        }
    }
}

/** Вычисляет дату выполнения для события: nextDate если есть, иначе lastDate + intervalDays. */
private fun dueAt(e: CareEvent, today: Long, dayMs: Long): Long? {
    e.nextDate?.let { return it }
    val last = e.lastDate ?: return null
    val interval = e.intervalDays ?: return null
    return last + (interval * dayMs).toLong()
}