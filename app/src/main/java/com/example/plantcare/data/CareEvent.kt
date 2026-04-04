package com.example.plantcare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.plantcare.db.Converters

@Entity(tableName = "care_events")
@TypeConverters(Converters::class)
data class CareEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val plantId: Int,
    val type: CareEventType,
    val intervalDays: Float? = null,      // Интервал (дней)
    val lastDate: Long? = null,           // Последняя дата выполнения
    val fertilizerType: String? = null,   // Тип удобрения
    val nextDate: Long? = null,           // Дата следующей пересадки
    val reminderDateTime: Long? = null,   // Точная дата и время напоминания (timestamp)
    val done: Boolean = false,
    val reminderEnabled: Boolean = false, // Включено ли напоминание
    val reminderTime: Long? = null        // Время суток для напоминания (millis с начала дня)
) 