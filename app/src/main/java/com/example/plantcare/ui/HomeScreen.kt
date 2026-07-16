package com.example.plantcare.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.plantcare.ui.components.ProxyIndicator
import com.example.plantcare.ui.components.ProxyStatusBottomSheet
import com.example.plantcare.sharedui.PlantCareDesign

data class HomeNavItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: () -> Unit
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    onPlants: () -> Unit,
    onNotes: () -> Unit,
    onReference: () -> Unit,
    onWeather: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNeural: () -> Unit,
    onDiagnosis: () -> Unit,
    onChatGPT: () -> Unit,
    onAbout: () -> Unit,
    onToday: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    val navItems = listOf(
        HomeNavItem("Сегодня", "Задачи по уходу", Icons.Filled.EventAvailable, onToday),
        HomeNavItem("Мои растения", "Уход и события", Icons.Filled.Yard, onPlants),
        HomeNavItem("Заметки", "Записи и напоминания", Icons.AutoMirrored.Filled.Note, onNotes),
        HomeNavItem("Справочник", "Поиск растений", Icons.AutoMirrored.Filled.MenuBook, onReference),
        HomeNavItem("ИИ-анализатор", "Фото-диагностика", Icons.Filled.Memory, onNeural),
        HomeNavItem("Диагностика", "По симптомам", Icons.Filled.BugReport, onDiagnosis),
        HomeNavItem("ИИ-ассистент", "Умный помощник", Icons.AutoMirrored.Filled.Chat, onChatGPT),
        HomeNavItem("Погода", "Прогноз для сада", Icons.Filled.WbSunny, onWeather),
        HomeNavItem("Настройки", "Тема, AI, прокси", Icons.Filled.Settings, onSettings),
        HomeNavItem("О нас", "Инфо и контакты", Icons.Filled.Info, onAbout)
    )

    var showProxyDetails by remember { mutableStateOf(false) }

    if (showProxyDetails) {
        ProxyStatusBottomSheet(onDismiss = { showProxyDetails = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = if (darkTheme) {
                    Brush.verticalGradient(
                        colors = listOf(
                            PlantCareDesign.Colors.BackgroundDark,
                            PlantCareDesign.Colors.SurfaceDark
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            PlantCareDesign.Colors.Background,
                            PlantCareDesign.Colors.SurfaceLight
                        )
                    )
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PlantCareDesign.Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌿", fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    ProxyIndicator(onShowDetails = { showProxyDetails = true })
                }
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    onClick = onToggleTheme
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = darkTheme,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith
                                    fadeOut(animationSpec = tween(300))
                            }
                        ) { isDark ->
                            Icon(
                                imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDark) "Светлая тема" else "Тёмная тема",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(800)) + slideInVertically(
                    initialOffsetY = { -40 },
                    animationSpec = tween(800)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PlantCare",
                        fontSize = 32.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Забота о ваших растениях",
                        fontSize = 16.sp,
                        color = PlantCareDesign.Colors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    Text(
                        text = "Создатель: Денис Аниськов",
                        fontSize = 13.sp,
                        color = PlantCareDesign.Colors.TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(navItems) { item ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(500)) + scaleIn(tween(500))
                    ) {
                        HomeNavCard(item = item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeNavCard(item: HomeNavItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        onClick = item.route,
        shape = MaterialTheme.shapes.large
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = item.subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
