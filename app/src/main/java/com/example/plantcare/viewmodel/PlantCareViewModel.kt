package com.example.plantcare.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.data.*
import com.example.plantcare.db.*
import com.example.plantcare.util.CareEventReminderManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlantCareViewModel(
    private val plantDao: PlantDao,
    private val careEventDao: CareEventDao,
    private val referencePlantDao: ReferencePlantDao,
    private val chatDao: ChatDao,
    applicationContext: Context // Передаем applicationContext
) : ViewModel() {

    private val aiClient = com.example.plantcare.ai.CascadeAiClient(applicationContext)

    val plants: StateFlow<List<Plant>> = plantDao.getAllPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val referencePlants: StateFlow<List<ReferencePlant>> = referencePlantDao.getAllReferencePlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _isGeneratingRecommendations = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val isGeneratingRecommendations: StateFlow<Map<Int, Boolean>> = _isGeneratingRecommendations.asStateFlow()

    private val eventsCache = mutableMapOf<Int, StateFlow<List<CareEvent>>>()

    fun getEventsForPlant(plantId: Int): StateFlow<List<CareEvent>> {
        return eventsCache.getOrPut(plantId) {
            careEventDao.getEventsForPlant(plantId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    fun addPlant(plant: Plant) {
        viewModelScope.launch { 
            val id = plantDao.insertPlant(plant)
            val newPlant = plant.copy(id = id.toInt())
            // После добавления запрашиваем рекомендации от ИИ
            fetchAiRecommendations(newPlant)
        }
    }

    private suspend fun fetchAiRecommendations(plant: Plant) {
        _isGeneratingRecommendations.value = _isGeneratingRecommendations.value + (plant.id to true)
        val result = aiClient.getPlantRecommendations(plant.name, plant.type)
        result.onSuccess { json ->
            val updatedPlant = plant.copy(aiRecommendations = json)
            plantDao.updatePlant(updatedPlant)
        }
        _isGeneratingRecommendations.value = _isGeneratingRecommendations.value + (plant.id to false)
    }

    /**
     * Возвращает предупреждение, если интервал ухода не соответствует рекомендации ИИ
     */
    fun checkCareEventInterval(plant: Plant, eventType: String, userIntervalDays: Float?): String? {
        val recommendations = plant.aiRecommendations ?: return null
        val days = userIntervalDays ?: 0f
        if (days <= 0f) return null // Если ничего не вписано или 0, не ругаемся
        if (days > 3650f) return "⚠️ Слишком большой интервал! Проверьте введенные данные."

        return try {
            val json = org.json.JSONObject(recommendations)
            when (eventType.lowercase()) {
                "полив" -> {
                    val rec = json.optInt("watering_days", 0)
                    if (rec > 0) {
                        if (days < rec) "⚠️ Слишком частый полив! ИИ рекомендует каждые $rec дн. Опасайтесь загнивания корней."
                        else if (days > rec + 3) "⚠️ Редкий полив! ИИ рекомендует каждые $rec дн. Растение может засохнуть."
                        else null
                    } else null
                }
                "подкормка" -> {
                    val rec = json.optInt("fertilizing_days", 0)
                    if (rec > 0) {
                        if (days < rec) "⚠️ Частая подкормка! ИИ рекомендует раз в $rec дн. Избыток солей вреден."
                        else if (days > rec * 1.5) "⚠️ Редкая подкормка! ИИ рекомендует раз в $rec дн."
                        else null
                    } else null
                }
                "опрыскивание" -> {
                    val rec = json.optInt("spraying_days", 0)
                    if (rec > 0) {
                        if (days < rec) "⚠️ Слишком частое опрыскивание! ИИ рекомендует раз в $rec дн."
                        else if (days > (rec * 1.5).toInt()) "⚠️ Редкое опрыскивание! ИИ рекомендует раз в $rec дн."
                        else null
                    } else null
                }
                "пересадка" -> {
                    val recMonths = json.optInt("replanting_months", 0)
                    val userMonths = days / 30f
                    if (recMonths > 0) {
                        if (userMonths < recMonths / 2f) "⚠️ Слишком частая пересадка! ИИ рекомендует раз в $recMonths мес. Это стресс для корней."
                        else if (userMonths > recMonths * 1.5) "⚠️ Запоздалая пересадка! ИИ рекомендует раз в $recMonths мес. Корням может стать тесно."
                        else null
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun checkFertilizer(plant: Plant, userFertilizer: String): String? {
        val recommendations = plant.aiRecommendations ?: return null
        if (userFertilizer.isBlank()) return null
        
        return try {
            val json = org.json.JSONObject(recommendations)
            val recType = json.optString("fertilizer_type", "")
            if (recType.isNotBlank() && recType != "null") {
                // Если введено что-то совсем другое
                val words = userFertilizer.lowercase().split(" ", "-", ".")
                val recWords = recType.lowercase().split(" ", "-", ".")
                val match = words.any { w -> w.length > 3 && recWords.any { rw -> rw.contains(w) || w.contains(rw) } }
                
                if (!match) {
                    "⚠️ Внимание: непроверенное удобрение. Рекомендуемые варианты: $recType"
                } else {
                    "✅ Подходящее удобрение ($recType)"
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun updatePlant(plant: Plant) {
        viewModelScope.launch { plantDao.updatePlant(plant) }
    }

    fun deletePlant(plant: Plant) {
        viewModelScope.launch { 
            plantDao.deletePlant(plant)
            eventsCache.remove(plant.id)
        }
    }

    fun addCareEvent(context: Context, event: CareEvent) {
        viewModelScope.launch {
            val id = careEventDao.insertEvent(event)
            val plant = plants.value.find { it.id == event.plantId }
            val plantName = plant?.name ?: "Растение"
            CareEventReminderManager.scheduleReminder(context, event.copy(id = id.toInt()), plantName)
        }
    }

    fun updateCareEvent(context: Context, event: CareEvent) {
        viewModelScope.launch {
            careEventDao.updateEvent(event)
            val plant = plants.value.find { it.id == event.plantId }
            val plantName = plant?.name ?: "Растение"
            CareEventReminderManager.scheduleReminder(context, event, plantName)
        }
    }

    fun deleteCareEvent(context: Context, event: CareEvent) {
        viewModelScope.launch {
            CareEventReminderManager.cancelReminder(context, event.id)
            careEventDao.deleteEvent(event)
        }
    }

    fun markEventDone(context: Context, event: CareEvent) {
        viewModelScope.launch {
            val updatedEvent = event.copy(done = true)
            careEventDao.updateEvent(updatedEvent)
            CareEventReminderManager.cancelReminder(context, event.id)
        }
    }

    fun searchReferencePlants(query: String): StateFlow<List<ReferencePlant>> {
        return referencePlantDao.searchReferencePlants("%$query%")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun toggleReferencePlantFavorite(plant: ReferencePlant) {
        viewModelScope.launch {
            referencePlantDao.updateFavorite(plant.id, !plant.isFavorite)
        }
    }

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun addNote(note: Note) {
        _notes.value = _notes.value + note
    }
    fun updateNote(note: Note) {
        _notes.value = _notes.value.map { if (it.id == note.id) note else it }
    }
    fun deleteNote(note: Note) {
        _notes.value = _notes.value.filter { it.id != note.id }
    }

    fun toggleNoteDone(note: Note) {
        updateNote(note.copy(done = !note.done))
    }

    val chatSessions: StateFlow<List<ChatSession>> = chatDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    val currentMessages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else chatDao.getMessagesForSession(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startNewChat() {
        viewModelScope.launch {
            val newSession = ChatSession(title = "Новый чат ${System.currentTimeMillis()}")
            val id = chatDao.insertSession(newSession)
            _currentSessionId.value = id
        }
    }

    fun selectChatSession(id: Long) {
        _currentSessionId.value = id
    }

    fun saveChatMessage(role: String, content: String) {
        val sessionId = _currentSessionId.value
        if (sessionId != null) {
            viewModelScope.launch {
                // Если это первое сообщение от пользователя, переименовываем чат
                if (role == "user" && currentMessages.value.isEmpty()) {
                    val newTitle = if (content.length > 30) {
                        content.take(27) + "..."
                    } else {
                        content
                    }.replace("\n", " ")
                    chatDao.updateSessionTitle(sessionId, newTitle)
                }

                chatDao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = role,
                        content = content
                    )
                )
            }
        }
    }

    fun deleteChatSession(session: ChatSession) {
        viewModelScope.launch {
            chatDao.deleteSession(session)
            if (_currentSessionId.value == session.id) {
                _currentSessionId.value = null
            }
        }
    }

    fun renameChatSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            chatDao.updateSessionTitle(sessionId, newTitle)
        }
    }
}
