package com.example.plantcare.db

import androidx.room.*
import com.example.plantcare.data.CareEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface CareEventDao {
    @Query("SELECT * FROM care_events WHERE plantId = :plantId")
    fun getEventsForPlant(plantId: Int): Flow<List<CareEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CareEvent)

    @Update
    suspend fun updateEvent(event: CareEvent)

    @Delete
    suspend fun deleteEvent(event: CareEvent)
} 