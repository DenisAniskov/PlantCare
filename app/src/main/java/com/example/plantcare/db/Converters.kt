package com.example.plantcare.db

import androidx.room.TypeConverter
import com.example.plantcare.data.CareEventType

class Converters {
    @TypeConverter
    fun fromCareEventType(value: CareEventType): String = value.name

    @TypeConverter
    fun toCareEventType(value: String): CareEventType = CareEventType.valueOf(value)
} 