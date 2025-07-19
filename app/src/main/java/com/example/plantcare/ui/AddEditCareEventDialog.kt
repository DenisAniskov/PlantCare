package com.example.plantcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantcare.data.CareEvent
import com.example.plantcare.data.CareEventType
import java.util.*
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import android.app.TimePickerDialog
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll

@Composable
fun AddEditCareEventDialog(
    initialEvent: CareEvent? = null,
    onDismiss: () -> Unit,
    onSave: (CareEvent) -> Unit,
    plantId: Int
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(initialEvent?.type ?: CareEventType.WATERING) }
    var intervalDays by remember { mutableStateOf(initialEvent?.intervalDays?.toString() ?: "") }
    var lastDate by remember { mutableStateOf(initialEvent?.lastDate ?: System.currentTimeMillis()) }
    var fertilizerType by remember { mutableStateOf(TextFieldValue(initialEvent?.fertilizerType ?: "")) }
    var nextDate by remember { mutableStateOf(initialEvent?.nextDate ?: System.currentTimeMillis()) }
    // Новые состояния для напоминания
    var reminderEnabled by remember { mutableStateOf(initialEvent?.reminderEnabled ?: false) }
    var reminderDateTime by remember { mutableStateOf(initialEvent?.reminderDateTime ?: System.currentTimeMillis()) }
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    fun showDatePicker(context: Context, initial: Long, onDate: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                }
                onDate(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    fun showTimePicker(context: Context, initial: Long, onTime: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val picked = Calendar.getInstance().apply {
                    timeInMillis = initial
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onTime(picked.timeInMillis)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialEvent == null) "Добавить событие ухода" else "Редактировать событие ухода",
                fontSize = 22.sp
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                Text("Тип события:", fontSize = 18.sp)
                CareEventType.values().forEach { eventType ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = type == eventType,
                            onClick = { type = eventType }
                        )
                        Text(
                            text = when (eventType) {
                                CareEventType.WATERING -> "Полив"
                                CareEventType.FERTILIZING -> "Подкормка"
                                CareEventType.SPRAYING -> "Опрыскивание"
                                CareEventType.REPOTTING -> "Пересадка"
                            },
                            fontSize = 18.sp
                        )
                    }
                }
                if (type == CareEventType.FERTILIZING) {
                    OutlinedTextField(
                        value = fertilizerType,
                        onValueChange = { fertilizerType = it },
                        label = { Text("Тип удобрения (опционально)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp)
                    )
                }
                // Поле для интервала (intervalDays)
                OutlinedTextField(
                    value = intervalDays,
                    onValueChange = { intervalDays = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Интервал (дней, часы: 0.5 = 12 ч, 0.25 = 6 ч)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                    singleLine = true
                )
                // Поле для выбора последней даты выполнения (lastDate)
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text("Последнее выполнение:", fontSize = 15.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("Дата:", fontSize = 14.sp)
                    IconButton(
                        onClick = { showDatePicker(context, lastDate) { picked -> lastDate = picked } },
                        modifier = Modifier
                            .size(28.dp)
                            .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = "Выбрать дату", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { showDatePicker(context, lastDate) { picked -> lastDate = picked } }, modifier = Modifier.defaultMinSize(minWidth = 0.dp)) {
                        Text(dateFormat.format(Date(lastDate)), fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Время:", fontSize = 14.sp)
                    IconButton(
                        onClick = { showTimePicker(context, lastDate) { picked -> lastDate = picked } },
                        modifier = Modifier
                            .size(28.dp)
                            .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = "Выбрать время",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { showTimePicker(context, lastDate) { picked -> lastDate = picked } }, modifier = Modifier.defaultMinSize(minWidth = 0.dp)) {
                        Text(timeFormat.format(Date(lastDate)), fontSize = 14.sp)
                    }
                }
                // Для пересадки: компактное поле выбора даты и времени следующей пересадки
                if (type == CareEventType.REPOTTING) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text("Следующая пересадка:", fontSize = 15.sp)
                        IconButton(
                            onClick = { showDatePicker(context, nextDate) { nextDate = it } },
                            modifier = Modifier
                                .size(28.dp)
                                .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Выбрать дату",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = { showDatePicker(context, nextDate) { nextDate = it } }, modifier = Modifier.defaultMinSize(minWidth = 0.dp)) {
                            Text(dateFormat.format(Date(nextDate)), fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Время:", fontSize = 14.sp)
                        IconButton(
                            onClick = { showTimePicker(context, nextDate) { picked -> nextDate = picked } },
                            modifier = Modifier
                                .size(28.dp)
                                .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = "Выбрать время",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = { showTimePicker(context, nextDate) { picked -> nextDate = picked } }, modifier = Modifier.defaultMinSize(minWidth = 0.dp)) {
                            Text(timeFormat.format(Date(nextDate)), fontSize = 14.sp)
                        }
                    }
                }
                // Блок напоминания (всегда после последнего выполнения, для всех типов)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Напоминание", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Checkbox(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                        Text("Напомнить:", fontSize = 14.sp)
                        if (reminderEnabled) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Дата:", fontSize = 13.sp)
                            IconButton(
                                onClick = {
                                    showDatePicker(context, reminderDateTime) { pickedDate ->
                                        val calOld = Calendar.getInstance().apply { timeInMillis = reminderDateTime }
                                        val calNew = Calendar.getInstance().apply { timeInMillis = pickedDate }
                                        calNew.set(Calendar.HOUR_OF_DAY, calOld.get(Calendar.HOUR_OF_DAY))
                                        calNew.set(Calendar.MINUTE, calOld.get(Calendar.MINUTE))
                                        calNew.set(Calendar.SECOND, 0)
                                        calNew.set(Calendar.MILLISECOND, 0)
                                        reminderDateTime = calNew.timeInMillis
                                    }
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = "Выбрать дату", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = {
                                showDatePicker(context, reminderDateTime) { pickedDate ->
                                    val calOld = Calendar.getInstance().apply { timeInMillis = reminderDateTime }
                                    val calNew = Calendar.getInstance().apply { timeInMillis = pickedDate }
                                    calNew.set(Calendar.HOUR_OF_DAY, calOld.get(Calendar.HOUR_OF_DAY))
                                    calNew.set(Calendar.MINUTE, calOld.get(Calendar.MINUTE))
                                    calNew.set(Calendar.SECOND, 0)
                                    calNew.set(Calendar.MILLISECOND, 0)
                                    reminderDateTime = calNew.timeInMillis
                                }
                            }, modifier = Modifier.defaultMinSize(minWidth = 0.dp)) {
                                Text(dateFormat.format(Date(reminderDateTime)), fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Время:", fontSize = 13.sp)
                            IconButton(
                                onClick = {
                                    showTimePicker(context, reminderDateTime) { pickedTime ->
                                        reminderDateTime = pickedTime
                                    }
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = "Выбрать время", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        TextButton(onClick = {
                                showTimePicker(context, reminderDateTime) { pickedTime ->
                                    reminderDateTime = pickedTime
                                }
                            }, modifier = Modifier.defaultMinSize(minWidth = 0.dp)) {
                                Text(timeFormat.format(Date(reminderDateTime)), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CareEvent(
                            id = initialEvent?.id ?: 0,
                            plantId = plantId,
                            type = type,
                            intervalDays = intervalDays.replace(',', '.').toFloatOrNull(),
                            lastDate = lastDate,
                            fertilizerType = if (type == CareEventType.FERTILIZING) fertilizerType.text else null,
                            nextDate = if (type == CareEventType.REPOTTING) nextDate else null,
                            done = false,
                            reminderEnabled = reminderEnabled,
                            reminderDateTime = if (reminderEnabled) reminderDateTime else null
                        )
                    )
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
} 