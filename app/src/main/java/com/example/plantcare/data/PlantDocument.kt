package com.example.plantcare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plant_documents")
data class PlantDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val plantId: Int?,
    val speciesName: String?,
    val filePath: String,
    val fileType: String,
    val description: String?,
    val addedAt: Long = System.currentTimeMillis()
)
