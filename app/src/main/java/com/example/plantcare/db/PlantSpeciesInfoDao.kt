package com.example.plantcare.db

import androidx.room.*
import com.example.plantcare.data.PlantSpeciesInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantSpeciesInfoDao {
    @Query("SELECT * FROM plant_species_info WHERE speciesName = :name")
    suspend fun getSpeciesInfo(name: String): PlantSpeciesInfo?

    @Query("SELECT * FROM plant_species_info WHERE speciesName = :name")
    fun getSpeciesInfoFlow(name: String): Flow<PlantSpeciesInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeciesInfo(info: PlantSpeciesInfo)

    @Delete
    suspend fun deleteSpeciesInfo(info: PlantSpeciesInfo)
}
