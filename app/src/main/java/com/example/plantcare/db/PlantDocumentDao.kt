package com.example.plantcare.db

import androidx.room.*
import com.example.plantcare.data.PlantDocument

@Dao
interface PlantDocumentDao {
    @Query("SELECT * FROM plant_documents WHERE plantId = :plantId")
    suspend fun getDocumentsForPlant(plantId: Int): List<PlantDocument>

    @Query("SELECT * FROM plant_documents WHERE speciesName = :speciesName")
    suspend fun getDocumentsForSpecies(speciesName: String): List<PlantDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: PlantDocument)

    @Delete
    suspend fun deleteDocument(doc: PlantDocument)
}
