package com.example.plantcare.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.plantcare.db.AppDatabase
import com.example.plantcare.viewmodel.PlantCareViewModel
import com.example.plantcare.db.PlantDao
import com.example.plantcare.db.CareEventDao
import com.example.plantcare.db.ReferencePlantDao
import com.example.plantcare.data.ReferencePlant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.example.plantcare.util.PlantJsonImporter
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class GTMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Запрос разрешения на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        lateinit var dbInstance: AppDatabase
        dbInstance = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "plantcare-db"
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        val refDao = dbInstance.referencePlantDao()
                        com.example.plantcare.util.PlantJsonImporter.importFromAssets(this@GTMainActivity, refDao)
                    }
                }
            })
            .fallbackToDestructiveMigration()
            .build()
        // --- Удалён импорт справочника из JSON после build() ---
        val plantDao: PlantDao = dbInstance.plantDao()
        val careEventDao: CareEventDao = dbInstance.careEventDao()
        val referencePlantDao: ReferencePlantDao = dbInstance.referencePlantDao()
        // Добавить импорт справочника при каждом запуске
        CoroutineScope(Dispatchers.IO).launch {
            com.example.plantcare.util.PlantJsonImporter.importFromAssets(this@GTMainActivity, referencePlantDao)
        }
        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: PlantCareViewModel = PlantCareViewModel(plantDao, careEventDao, referencePlantDao)
                    PlantCareApp(viewModel)
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Можно обработать результат, если нужно
    }
} 