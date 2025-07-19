package com.example.plantcare.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.plantcare.R
import android.util.Log

class CareEventReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        Log.d("CareEventReminderWorker", "doWork called for eventId=${inputData.getInt("eventId", -1)}")
        val eventId = inputData.getInt("eventId", -1)
        val plantId = inputData.getInt("plantId", -1)
        val plantName = inputData.getString("plantName") ?: ""
        val eventType = inputData.getString("eventType") ?: ""
        val channelId = "care_event_reminder_channel"
        val notificationId = eventId
        val eventTypeWithEmoji = when (eventType) {
            "Полив" -> "Полив 🌊"
            "Подкормка" -> "Подкормка 🌱"
            "Опрыскивание" -> "Опрыскивание 💦"
            "Пересадка" -> "Пересадка 🏺"
            else -> eventType
        }
        val contentText = if (plantName.isNotBlank()) {
            "Пора выполнить: $eventTypeWithEmoji для растения $plantName"
        } else {
            "Пора выполнить: $eventTypeWithEmoji"
        }
        val smallIcon = when (eventType) {
            "Полив" -> com.example.plantcare.R.drawable.ic_weather_drizzle
            "Подкормка" -> com.example.plantcare.R.drawable.ic_weather_partly_cloudy
            "Опрыскивание" -> com.example.plantcare.R.drawable.ic_weather_rain
            "Пересадка" -> com.example.plantcare.R.drawable.ic_weather_snow
            else -> com.example.plantcare.R.mipmap.ic_launcher_round
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания о событиях ухода",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle("Напоминание о событии ухода")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId, builder.build())
        }
        return Result.success()
    }
} 