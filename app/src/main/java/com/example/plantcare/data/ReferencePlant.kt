package com.example.plantcare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reference_plants")
data class ReferencePlant(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val imageRes: String?, // имя ресурса или url, можно null
    val watering: String?, // рекомендации по поливу
    val light: String?,    // требования к освещению
    val temperature: String?, // температурный режим
    val fertilizing: String?, // рекомендации по подкормке
    val notes: String?,    // дополнительные заметки
    val toxicityNote: String?, // текстовое описание токсичности
    val isFavorite: Boolean = false // избранное
) 