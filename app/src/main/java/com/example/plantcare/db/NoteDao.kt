package com.example.plantcare.db

import androidx.room.*
import com.example.plantcare.data.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY date DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE plantId = :plantId ORDER BY date DESC")
    fun getNotesForPlant(plantId: Int): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("UPDATE notes SET done = :done WHERE id = :noteId")
    suspend fun setDone(noteId: Int, done: Boolean)
}