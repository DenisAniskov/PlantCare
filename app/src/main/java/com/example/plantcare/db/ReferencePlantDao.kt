package com.example.plantcare.db

import androidx.room.*
import com.example.plantcare.data.ReferencePlant
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferencePlantDao {
    @Query("SELECT * FROM reference_plants ORDER BY name ASC")
    fun getAllReferencePlants(): Flow<List<ReferencePlant>>

    @Query("SELECT * FROM reference_plants WHERE name LIKE :query ORDER BY name ASC")
    fun searchReferencePlants(query: String): Flow<List<ReferencePlant>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plants: List<ReferencePlant>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plant: ReferencePlant)

    @Delete
    suspend fun delete(plant: ReferencePlant)

    @Query("DELETE FROM reference_plants")
    suspend fun clearAll()

    @Query("UPDATE reference_plants SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFavorite: Boolean)
} 