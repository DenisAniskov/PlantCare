package com.example.plantcare.sharedui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единая система дизайна для PlantCare (Android + Desktop)
 * Красивая современная палитра с природными оттенками
 */
object PlantCareDesign {
    // Цветовая палитра - природные зеленые тона с градиентами
    object Colors {
        // Основные цвета - более яркие и насыщенные зеленые
        val Primary = Color(0xFF2E7D32) // Насыщенный зеленый
        val PrimaryLight = Color(0xFF60AD5E) // Светло-зеленый для градиентов
        val PrimaryDark = Color(0xFF005005) // Темно-зеленый
        val PrimaryVariant = Color(0xFF66BB6A) // Яркий зеленый акцент
        
        // Вторичные цвета
        val Secondary = Color(0xFF43A047) // Свежий зеленый
        val SecondaryLight = Color(0xFF76D275)
        val SecondaryDark = Color(0xFF00701A)
        
        // Дополнительные акцентные цвета природы
        val Mint = Color(0xFF81C784) // Мятный
        val Lime = Color(0xFF9CCC65) // Лаймовый
        val Forest = Color(0xFF388E3C) // Лесной
        val Emerald = Color(0xFF00C853) // Изумрудный
        
        // Фоны
        val Background = Color(0xFFF5F9F5) // Очень светло-зеленый
        val BackgroundDark = Color(0xFF121212) // Темный фон
        val Surface = Color(0xFFFFFFFF) // Поверхность
        val SurfaceLight = Color(0xFFFAFDFA) // Светлая поверхность с оттенком
        val SurfaceDark = Color(0xFF1E1E1E) // Темная поверхность
        val SurfaceElevated = Color(0xFFFFFFFE) // Приподнятая поверхность
        
        // Текст
        val OnPrimary = Color(0xFFFFFFFF)
        val OnSecondary = Color(0xFFFFFFFF)
        val OnBackground = Color(0xFF1B1B1B)
        val OnBackgroundDark = Color(0xFFE8E8E8)
        val OnSurface = Color(0xFF1B1B1B)
        val OnSurfaceDark = Color(0xFFE8E8E8)
        val TextSecondary = Color(0xFF5F6368) // Вторичный текст
        val TextTertiary = Color(0xFF9AA0A6) // Третичный текст
        
        // Акцентные цвета
        val Success = Color(0xFF4CAF50)
        val SuccessLight = Color(0xFF81C784)
        val Warning = Color(0xFFFF9800)
        val WarningLight = Color(0xFFFFB74D)
        val Error = Color(0xFFE53935)
        val ErrorLight = Color(0xFFEF5350)
        val Info = Color(0xFF2196F3)
        val InfoLight = Color(0xFF64B5F6)
        
        // Тени и границы
        val Shadow = Color(0x40000000)
        val ShadowLight = Color(0x20000000)
        val Border = Color(0xFFE0E0E0)
        val BorderDark = Color(0xFF3A3A3A)
        val BorderLight = Color(0xFFF0F0F0)
    }
    
    // Градиенты
    object Gradients {
        val PrimaryGradient = Brush.horizontalGradient(
            colors = listOf(Colors.Primary, Colors.PrimaryLight)
        )
        val SecondaryGradient = Brush.horizontalGradient(
            colors = listOf(Colors.Secondary, Colors.SecondaryLight)
        )
        val SuccessGradient = Brush.horizontalGradient(
            colors = listOf(Colors.Success, Colors.SuccessLight)
        )
        val NatureGradient = Brush.verticalGradient(
            colors = listOf(Colors.Mint, Colors.Forest)
        )
        val SunsetGradient = Brush.horizontalGradient(
            colors = listOf(Colors.Lime, Colors.PrimaryLight, Colors.Primary)
        )
    }
    
    // Размеры
    object Spacing {
        val ExtraSmall = 4.dp
        val Small = 8.dp
        val Medium = 16.dp
        val Large = 24.dp
        val ExtraLarge = 32.dp
    }
    
    // Типографика
    object Typography {
        val HeadlineLarge = 36.sp
        val HeadlineMedium = 28.sp
        val HeadlineSmall = 24.sp
        val TitleLarge = 22.sp
        val TitleMedium = 18.sp
        val TitleSmall = 16.sp
        val BodyLarge = 18.sp
        val BodyMedium = 16.sp
        val BodySmall = 14.sp
        val LabelLarge = 16.sp
        val LabelMedium = 14.sp
        val LabelSmall = 12.sp
    }
    
    // Радиусы скругления
    object Corners {
        val Small = 4.dp
        val Medium = 8.dp
        val Large = 12.dp
        val ExtraLarge = 16.dp
    }
    
    // Размеры кнопок
    object ButtonSizes {
        val MinHeight = 48.dp // Для доступности
        val MinWidth = 64.dp
        val IconSize = 24.dp
    }
}
