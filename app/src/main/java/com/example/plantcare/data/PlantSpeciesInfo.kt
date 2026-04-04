package com.example.plantcare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plant_species_info")
data class PlantSpeciesInfo(
    @PrimaryKey val speciesName: String,
    val wateringDays: Int?,
    val lightRequirement: String?,
    val soilType: String?,
    val careNotes: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)
