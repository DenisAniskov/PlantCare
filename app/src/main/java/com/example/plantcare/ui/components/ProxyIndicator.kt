package com.example.plantcare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.plantcare.network.ProxySentinel
import com.example.plantcare.network.ProxyStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProxyIndicator(onShowDetails: () -> Unit) {
    val status by ProxySentinel.status.collectAsState()
    
    val color = when (status) {
        ProxyStatus.ACTIVE -> Color.Green
        ProxyStatus.CHECKING -> Color.Cyan
        ProxyStatus.UNSTABLE -> Color.Yellow
        ProxyStatus.OFFLINE -> Color.Red
    }

    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(24.dp)
            .clickable { onShowDetails() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyStatusBottomSheet(onDismiss: () -> Unit) {
    val status by ProxySentinel.status.collectAsState()
    val lastChecked by ProxySentinel.lastChecked.collectAsState()
    val attempt by ProxySentinel.currentAttempt.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Proxy Status", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status.name,
                color = when (status) {
                    ProxyStatus.ACTIVE -> Color.Green
                    ProxyStatus.CHECKING -> Color.Cyan
                    ProxyStatus.UNSTABLE -> Color.Yellow
                    ProxyStatus.OFFLINE -> Color.Red
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (status == ProxyStatus.CHECKING) {
                Text(text = "Проверка... Попытка $attempt из 3")
            } else if (status == ProxyStatus.UNSTABLE) {
                Text(text = "Нестабильно. Попытка $attempt из 3")
            } else if (status == ProxyStatus.OFFLINE) {
                Text(text = "Не работает после 3 попыток", color = Color.Red)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Последняя проверка: ${sdf.format(Date(lastChecked))}")
            Spacer(modifier = Modifier.height(24.dp))
            val scope = rememberCoroutineScope()
            Button(onClick = { 
                scope.launch {
                    ProxySentinel.checkStatus()
                }
            }) {
                Text("Проверить")
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
