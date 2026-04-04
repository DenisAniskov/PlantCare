package com.example.plantcare.util

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.example.plantcare.data.CareEvent
import android.util.Log
import com.example.plantcare.data.CareEventType

object CareEventReminderManager {
    fun scheduleReminder(context: Context, event: CareEvent, plantName: String) {
        if (!event.reminderEnabled || event.reminderDateTime == null) return
        val now = System.currentTimeMillis()
        val delay = event.reminderDateTime - now
        if (delay < 0) return // Не планируем в прошлом
        Log.d("CareEventReminderManager", "Scheduling reminder for eventId=${event.id}, delay=${delay/1000}s, reminderDateTime=${event.reminderDateTime}, now=${now}")
        val eventTypeLocalized = when (event.type) {
            CareEventType.WATERING -> "Полив"
            CareEventType.FERTILIZING -> "Подкормка"
            CareEventType.SPRAYING -> "Опрыскивание"
            CareEventType.REPOTTING -> "Пересадка"
        }
        val data = workDataOf(
            "eventId" to event.id,
            "plantId" to event.plantId,
            "plantName" to plantName,
            "eventType" to eventTypeLocalized
        )
        val request = OneTimeWorkRequestBuilder<CareEventReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("care_event_reminder_${event.id}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "care_event_reminder_${event.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(context: Context, eventId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork("care_event_reminder_$eventId")
    }
} 