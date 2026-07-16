package com.example.plantcare.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notes", indices = [Index("plantId")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val date: Long = System.currentTimeMillis(),
    val plantId: Int? = null,
    val done: Boolean = false
)