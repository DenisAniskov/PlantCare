package com.example.plantcare.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import com.example.plantcare.ui.components.ProxyIndicator
import com.example.plantcare.ui.components.ProxyStatusBottomSheet
import com.example.plantcare.viewmodel.PlantCareViewModel
import com.example.plantcare.data.Plant
import com.example.plantcare.data.CareEvent
import com.example.plantcare.data.ReferencePlant
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantsScreen(
    viewModel: PlantCareViewModel,
    onBack: () -> Unit,
    showAddPlantDialog: Boolean,
    setShowAddPlantDialog: (Boolean) -> Unit,
    showEditPlantDialog: Plant?,
    setShowEditPlantDialog: (Plant?) -> Unit,
    showDeletePlantDialog: Plant?,
    setShowDeletePlantDialog: (Plant?) -> Unit,
    selectedPlant: Plant?,
    setSelectedPlant: (Plant?) -> Unit,
    showAddEventDialog: Boolean,
    setShowAddEventDialog: (Boolean) -> Unit,
    showEditEventDialog: CareEvent?,
    setShowEditEventDialog: (CareEvent?) -> Unit,
    showDeleteEventDialog: CareEvent?,
    setShowDeleteEventDialog: (CareEvent?) -> Unit
) {
    val plants by viewModel.plants.collectAsState()
    val referencePlants by viewModel.referencePlants.collectAsState()
    val context = LocalContext.current
    var showProxyDetails by remember { mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
    }

    if (showAddPlantDialog) {
        AddEditPlantDialog(
            onDismiss = { setShowAddPlantDialog(false) },
            onSave = {
                viewModel.addPlant(it)
                setShowAddPlantDialog(false)
            },
            referencePlants = referencePlants
        )
    }
    if (showEditPlantDialog != null) {
        AddEditPlantDialog(
            initialPlant = showEditPlantDialog,
            onDismiss = { setShowEditPlantDialog(null) },
            onSave = {
                viewModel.updatePlant(it)
                setShowEditPlantDialog(null)
            },
            referencePlants = referencePlants
        )
    }
    if (showDeletePlantDialog != null) {
        val plantToDelete = showDeletePlantDialog
        AlertDialog(
            onDismissRequest = { setShowDeletePlantDialog(null) },
            title = { Text("Удалить растение?", fontSize = 22.sp) },
            text = { Text("Вы уверены, что хотите удалить растение \"${plantToDelete.name}\" и все связанные события?", fontSize = 18.sp) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deletePlant(plantToDelete)
                    setShowDeletePlantDialog(null)
                }) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { setShowDeletePlantDialog(null) }) { Text("Отмена") }
            }
        )
    }

    if (selectedPlant == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Мои растения", fontSize = 22.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        ProxyIndicator(onShowDetails = { showProxyDetails = true })
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                MainScreen(
                    plants = plants,
                    onPlantClick = { plant -> setSelectedPlant(plant) },
                    onAddPlant = { setShowAddPlantDialog(true) },
                    onEditPlant = { plant -> setShowEditPlantDialog(plant) },
                    onDeletePlant = { plant -> setShowDeletePlantDialog(plant) },
                    onShowReference = null,
                    onShowWeather = null
                )
            }
        }
    } else {
        val events by viewModel.getEventsForPlant(selectedPlant.id).collectAsState()
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val stableEvents = remember(events) { events.filter { it.id != 0 } }

        if (showAddEventDialog) {
            AddEditCareEventDialog(
                plantId = selectedPlant.id,
                onDismiss = { setShowAddEventDialog(false) },
                onSave = {
                    viewModel.addCareEvent(context, it)
                    setShowAddEventDialog(false)
                },
                viewModel = viewModel
            )
        }
        if (showEditEventDialog != null) {
            AddEditCareEventDialog(
                initialEvent = showEditEventDialog,
                plantId = selectedPlant.id,
                onDismiss = { setShowEditEventDialog(null) },
                onSave = {
                    viewModel.updateCareEvent(context, it)
                    setShowEditEventDialog(null)
                },
                viewModel = viewModel
            )
        }
        if (showDeleteEventDialog != null) {
            val evToDelete = showDeleteEventDialog
            AlertDialog(
                onDismissRequest = { setShowDeleteEventDialog(null) },
                title = { Text("Удалить событие?", fontSize = 22.sp) },
                text = { Text("Удалить событие ухода для растения \"${selectedPlant.name}\"?", fontSize = 18.sp) },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteCareEvent(context, evToDelete)
                        setShowDeleteEventDialog(null)
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { setShowDeleteEventDialog(null) }) { Text("Отмена") }
                }
            )
        }

        PlantDetailScreen(
            plant = selectedPlant,
            events = stableEvents,
            listState = listState,
            onBack = { setSelectedPlant(null) },
            onAddEvent = { setShowAddEventDialog(true) },
            onMarkEventDone = { event -> viewModel.markEventDone(context, event) },
            onEditEvent = { event -> setShowEditEventDialog(event) },
            onDeleteEvent = { event -> setShowDeleteEventDialog(event) }
        )
    }
} 