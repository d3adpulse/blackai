package com.blackai.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Варианты фона приложения - переключаются в настройках. */
enum class BackgroundStyle(val label: String) {
    PURE_BLACK("Чёрный"),
    GRAPHITE("Графит"),
    DEEP_SPACE("Космос"),
    NEON_PURPLE("Неон"),
    OCEAN("Океан"),
    FOREST("Лес"),
    WINE("Вино"),
    SUNSET("Закат")
}

@Composable
fun BackgroundBrush(style: BackgroundStyle): Brush = when (style) {
    BackgroundStyle.PURE_BLACK -> Brush.verticalGradient(listOf(BlackBase, BlackBase))
    BackgroundStyle.GRAPHITE -> Brush.verticalGradient(listOf(Charcoal, BlackBase))
    BackgroundStyle.DEEP_SPACE -> Brush.radialGradient(
        colors = listOf(DeepPurple, BlackBase, BlackBase),
        radius = 1400f
    )
    BackgroundStyle.NEON_PURPLE -> Brush.linearGradient(listOf(Violet, Magenta, BlackBase))
    BackgroundStyle.OCEAN -> Brush.linearGradient(listOf(Navy, CyanDark, BlackBase))
    BackgroundStyle.FOREST -> Brush.linearGradient(listOf(Forest, Color(0xFF0B2B1B), BlackBase))
    BackgroundStyle.WINE -> Brush.linearGradient(listOf(Wine, Color(0xFF3A0F2A), BlackBase))
    BackgroundStyle.SUNSET -> Brush.linearGradient(listOf(Color(0xFF2A0F2E), Color(0xFF7A2A1F), BlackBase))
}

/** Основной фон приложения с выбранным градиентом. */
@Composable
fun AppBackground(style: BackgroundStyle, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush(style))
    ) {
        content()
    }
}