package com.example.plantcare.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.plantcare.sharedui.PlantCareDesign

private val LightColorScheme = lightColorScheme(
    primary = PlantCareDesign.Colors.Primary,
    secondary = PlantCareDesign.Colors.Secondary,
    background = PlantCareDesign.Colors.Background,
    surface = PlantCareDesign.Colors.Surface,
    onPrimary = PlantCareDesign.Colors.OnPrimary,
    onBackground = PlantCareDesign.Colors.OnBackground,
    error = PlantCareDesign.Colors.Error
)

private val DarkColorScheme = darkColorScheme(
    primary = PlantCareDesign.Colors.PrimaryVariant,
    secondary = PlantCareDesign.Colors.Secondary,
    background = PlantCareDesign.Colors.BackgroundDark,
    surface = PlantCareDesign.Colors.SurfaceDark,
    onPrimary = PlantCareDesign.Colors.OnPrimary,
    onBackground = PlantCareDesign.Colors.OnBackgroundDark,
    error = PlantCareDesign.Colors.Error
)

@Composable
fun PlantCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
} 