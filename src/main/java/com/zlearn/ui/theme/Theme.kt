package com.zlearn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DeepBlue,
    secondary = DeepBlue,
    background = LightYellow,
    surface = CardWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    secondaryContainer = Color(0xFF99B3CB), // 选中背景色（深蓝变体）
    onSecondaryContainer = Color.White // 选中内容色
)

private val LightColorScheme = lightColorScheme(
    primary = DeepBlue,
    secondary = DeepBlue,
    background = LightYellow,
    surface = CardWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    secondaryContainer = Color(0xFF233366), // 选中背景色（深蓝变体）
    onSecondaryContainer = Color.White // 选中内容色
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}