package com.example.plantcare.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.plantcare.data.Plant
import com.example.plantcare.data.CareEvent
import com.example.plantcare.data.ReferencePlant
import com.example.plantcare.data.PlantSpeciesInfo
import com.example.plantcare.data.PlantDocument
import com.example.plantcare.data.ChatSession
import com.example.plantcare.data.ChatMessageEntity

@Database(
    entities = [
        Plant::class,
        CareEvent::class,
        ReferencePlant::class,
        PlantSpeciesInfo::class,
        PlantDocument::class,
        ChatSession::class,
        ChatMessageEntity::class
    ],
    version = 9
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun careEventDao(): CareEventDao
    abstract fun referencePlantDao(): ReferencePlantDao
    abstract fun plantSpeciesInfoDao(): PlantSpeciesInfoDao
    abstract fun plantDocumentDao(): PlantDocumentDao
    abstract fun chatDao(): ChatDao
} 