package com.example.plantcare.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Общие UI компоненты для PlantCare
 */

/**
 * Основная кнопка приложения с красивым градиентом и тенью
 */
@Composable
fun PlantCareButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PlantCareDesign.ButtonSizes.MinHeight + 16.dp)
            .padding(horizontal = PlantCareDesign.Spacing.Small, vertical = PlantCareDesign.Spacing.ExtraSmall)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(PlantCareDesign.Corners.Large),
                spotColor = PlantCareDesign.Colors.Primary.copy(alpha = 0.4f)
            ),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = PlantCareDesign.Colors.Primary,
            contentColor = PlantCareDesign.Colors.OnPrimary,
            disabledContainerColor = PlantCareDesign.Colors.Primary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(PlantCareDesign.Corners.Large),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 12.dp,
            hoveredElevation = 6.dp
        ),
        contentPadding = PaddingValues(
            horizontal = PlantCareDesign.Spacing.Medium,
            vertical = PlantCareDesign.Spacing.Medium
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(PlantCareDesign.ButtonSizes.IconSize)
                )
                Spacer(modifier = Modifier.width(PlantCareDesign.Spacing.Small))
            }
            Text(
                text = text,
                fontSize = PlantCareDesign.Typography.BodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = PlantCareDesign.Typography.BodyLarge * 1.2f
            )
        }
    }
}

/**
 * Вторичная кнопка (outlined)
 */
@Composable
fun PlantCareOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(PlantCareDesign.ButtonSizes.MinHeight)
            .padding(vertical = PlantCareDesign.Spacing.Small),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PlantCareDesign.Colors.Primary
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            fontSize = PlantCareDesign.Typography.TitleMedium
        )
    }
}

/**
 * Заголовок экрана
 */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = PlantCareDesign.Typography.HeadlineLarge,
        color = PlantCareDesign.Colors.Primary,
        modifier = modifier.padding(bottom = PlantCareDesign.Spacing.Medium)
    )
}

/**
 * Подзаголовок
 */
@Composable
fun Subtitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = PlantCareDesign.Typography.BodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

/**
 * Карточка с единым стилем
 */
@Composable
fun PlantCareCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PlantCareDesign.Spacing.Small),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(PlantCareDesign.Spacing.Medium),
            content = content
        )
    }
}

/**
 * Кнопка "Назад"
 */
@Composable
fun BackButton(
    onBack: () -> Unit,
    text: String = "← Назад",
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onBack,
        modifier = modifier.padding(PlantCareDesign.Spacing.Small)
    ) {
        Text(
            text = text,
            fontSize = PlantCareDesign.Typography.TitleMedium,
            color = PlantCareDesign.Colors.Primary
        )
    }
}
