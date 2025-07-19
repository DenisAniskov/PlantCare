package com.example.plantcare.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.data.Plant
import com.example.plantcare.data.CareEvent
import com.example.plantcare.data.CareEventType
import com.example.plantcare.data.ReferencePlant
import com.example.plantcare.data.Note
import com.example.plantcare.db.PlantDao
import com.example.plantcare.db.CareEventDao
import com.example.plantcare.db.ReferencePlantDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Context
import com.example.plantcare.util.CareEventReminderManager

class PlantCareViewModel(
    private val plantDao: PlantDao,
    private val careEventDao: CareEventDao,
    private val referencePlantDao: ReferencePlantDao
) : ViewModel() {
    val plants: StateFlow<List<Plant>> = plantDao.getAllPlants().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val referencePlants: StateFlow<List<ReferencePlant>> = referencePlantDao.getAllReferencePlants().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _selectedPlant = MutableStateFlow<Plant?>(null)
    val selectedPlant: StateFlow<Plant?> = _selectedPlant.asStateFlow()

    private val _selectedReferencePlant = MutableStateFlow<ReferencePlant?>(null)
    val selectedReferencePlant: StateFlow<ReferencePlant?> = _selectedReferencePlant.asStateFlow()

    fun selectPlant(plant: Plant) {
        _selectedPlant.value = plant
    }

    fun selectReferencePlant(plant: ReferencePlant) {
        _selectedReferencePlant.value = plant
    }

    fun addPlant(plant: Plant) {
        viewModelScope.launch { plantDao.insertPlant(plant) }
    }

    fun updatePlant(plant: Plant) {
        viewModelScope.launch { plantDao.updatePlant(plant) }
    }

    fun deletePlant(plant: Plant) {
        viewModelScope.launch { plantDao.deletePlant(plant) }
    }

    fun getEventsForPlant(plantId: Int): StateFlow<List<CareEvent>> =
        careEventDao.getEventsForPlant(plantId).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    fun addCareEvent(context: Context, event: CareEvent) {
        viewModelScope.launch {
            careEventDao.insertEvent(event)
            val plantName = plants.value.find { it.id == event.plantId }?.name ?: ""
            CareEventReminderManager.scheduleReminder(context, event, plantName)
        }
    }

    fun updateCareEvent(context: Context, event: CareEvent) {
        viewModelScope.launch {
            careEventDao.updateEvent(event)
            val plantName = plants.value.find { it.id == event.plantId }?.name ?: ""
            CareEventReminderManager.scheduleReminder(context, event, plantName)
        }
    }

    fun deleteCareEvent(context: Context, event: CareEvent) {
        viewModelScope.launch {
            careEventDao.deleteEvent(event)
            CareEventReminderManager.cancelReminder(context, event.id)
        }
    }

    fun markEventDone(context: Context, event: CareEvent) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updatedEvent = event.copy(done = true, lastDate = now)
            careEventDao.updateEvent(updatedEvent)
            val plantName = plants.value.find { it.id == event.plantId }?.name ?: ""
            CareEventReminderManager.scheduleReminder(context, updatedEvent, plantName)
        }
    }

    fun searchReferencePlants(query: String): StateFlow<List<ReferencePlant>> =
        referencePlantDao.searchReferencePlants("%$query%").stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

    fun toggleReferencePlantFavorite(plant: ReferencePlant) {
        viewModelScope.launch {
            referencePlantDao.updateFavorite(plant.id, !plant.isFavorite)
        }
    }

    // Заметки (in-memory, можно заменить на Room при необходимости)
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun addNote(note: Note) {
        _notes.value = _notes.value + note.copy(id = (_notes.value.maxOfOrNull { it.id } ?: 0) + 1)
    }
    fun updateNote(note: Note) {
        _notes.value = _notes.value.map { if (it.id == note.id) note else it }
    }
    fun deleteNote(note: Note) {
        _notes.value = _notes.value.filter { it.id != note.id }
    }

    fun toggleNoteDone(note: Note) {
        _notes.value = _notes.value.map { if (it.id == note.id) it.copy(done = !it.done) else it }
    }
} 