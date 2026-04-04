package com.example.plantcare.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.plantcare.db.AppDatabase
import com.example.plantcare.viewmodel.PlantCareViewModel
import com.example.plantcare.db.PlantDao
import com.example.plantcare.db.CareEventDao
import com.example.plantcare.db.ReferencePlantDao
import com.example.plantcare.db.PlantSpeciesInfoDao
import com.example.plantcare.db.PlantDocumentDao
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
import com.example.plantcare.util.CrashLogger
import com.example.plantcare.util.Prefs
import com.example.plantcare.ai.AiService
import com.example.plantcare.ai.PlantClassifierImpl
import com.example.plantcare.network.ProxySentinel
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start proxy monitoring
        ProxySentinel.startMonitoring(lifecycleScope)
        // Запрос разрешения на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val dbInstance = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "plantcare-db"
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // The actual population needs the instance, so we do it in a way that avoids the lateinit issue if possible
                    // However, for simplicity in this migration, we'll keep the logic but ensure it runs correctly
                }
            })
            .fallbackToDestructiveMigration()
            .build()

        val plantDao: PlantDao = dbInstance.plantDao()
        val careEventDao: CareEventDao = dbInstance.careEventDao()
        val referencePlantDao: ReferencePlantDao = dbInstance.referencePlantDao()
        val plantSpeciesInfoDao: PlantSpeciesInfoDao = dbInstance.plantSpeciesInfoDao()
        val plantDocumentDao: PlantDocumentDao = dbInstance.plantDocumentDao()
        
        // Initialize AI components
        val classifier = PlantClassifierImpl(this)
        val aiService = AiService(referencePlantDao, classifier)

        // Ensure database is populated (one-time)
        lifecycleScope.launch(Dispatchers.IO) {
            if (referencePlantDao.searchReferencePlants("%%").first().isEmpty()) {
                PlantJsonImporter.importFromAssets(this@MainActivity, referencePlantDao)
            }
        }

        setContent {
            val viewModel = PlantCareViewModel(plantDao, careEventDao, referencePlantDao)
            PlantCareApp(viewModel, aiService)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        // Можно обработать результат, если нужно
    }
}
