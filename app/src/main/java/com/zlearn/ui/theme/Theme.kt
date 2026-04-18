package com.zlearn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeVariant {
    EyeCareNote,
    BlueNote,
    GreenNote,
}

private data class ThemePalette(
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme,
)

private val eyeCarePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF2D4B8F),
        onPrimary = Color.White,
        secondary = Color(0xFF6A5D2D),
        onSecondary = Color.White,
        background = Color(0xFFF6F1DE), // 便签纸暖底色，护眼低刺激
        onBackground = Color(0xFF2C2A24),
        surface = Color(0xFFFFFBF0),
        onSurface = Color(0xFF2C2A24),
        surfaceVariant = Color(0xFFEAE3CF),
        onSurfaceVariant = Color(0xFF4E4B42),
        primaryContainer = Color(0xFFD9E3FF),
        onPrimaryContainer = Color(0xFF0E214D),
        secondaryContainer = Color(0xFFEFE3B8),
        onSecondaryContainer = Color(0xFF3F3412),
        tertiaryContainer = Color(0xFFE7E8D0),
        onTertiaryContainer = Color(0xFF273124),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFB5C7FF),
        onPrimary = Color(0xFF11295E),
        secondary = Color(0xFFD8CA93),
        onSecondary = Color(0xFF362D0A),
        background = Color(0xFF1F221F), // 暗色保留便签纸质感，降低蓝光感
        onBackground = Color(0xFFE6E2D6),
        surface = Color(0xFF272B27),
        onSurface = Color(0xFFE6E2D6),
        surfaceVariant = Color(0xFF3A3E38),
        onSurfaceVariant = Color(0xFFC8C5B8),
        primaryContainer = Color(0xFF2A3F73),
        onPrimaryContainer = Color(0xFFD9E3FF),
        secondaryContainer = Color(0xFF54481E),
        onSecondaryContainer = Color(0xFFF2E5B8),
        tertiaryContainer = Color(0xFF3D4738),
        onTertiaryContainer = Color(0xFFDDE6D3),
        errorContainer = Color(0xFF5A1C1C),
        onErrorContainer = Color(0xFFFFDAD6),
    )
)

private val blueNotePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF1F4B80),
        onPrimary = Color.White,
        secondary = Color(0xFF3F608C),
        onSecondary = Color.White,
        background = Color(0xFFF2F5FA),
        onBackground = Color(0xFF1D2630),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1D2630),
        surfaceVariant = Color(0xFFDEE6F3),
        onSurfaceVariant = Color(0xFF434C58),
        primaryContainer = Color(0xFFD5E3FF),
        onPrimaryContainer = Color(0xFF001C3B),
        secondaryContainer = Color(0xFFD8E4FF),
        onSecondaryContainer = Color(0xFF0A2345),
        tertiaryContainer = Color(0xFFE2E9F5),
        onTertiaryContainer = Color(0xFF202A36),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFA8C8FF),
        onPrimary = Color(0xFF003061),
        secondary = Color(0xFFB6CCEE),
        onSecondary = Color(0xFF1A334E),
        background = Color(0xFF161C23),
        onBackground = Color(0xFFDDE3EE),
        surface = Color(0xFF1D242C),
        onSurface = Color(0xFFDDE3EE),
        surfaceVariant = Color(0xFF3A434F),
        onSurfaceVariant = Color(0xFFBEC7D4),
        primaryContainer = Color(0xFF214978),
        onPrimaryContainer = Color(0xFFD5E3FF),
        secondaryContainer = Color(0xFF334C68),
        onSecondaryContainer = Color(0xFFD8E4FF),
        tertiaryContainer = Color(0xFF35414F),
        onTertiaryContainer = Color(0xFFE2E9F5),
        errorContainer = Color(0xFF5A1C1C),
        onErrorContainer = Color(0xFFFFDAD6),
    )
)

private val greenNotePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF2C6A4A),
        onPrimary = Color.White,
        secondary = Color(0xFF4C6B57),
        onSecondary = Color.White,
        background = Color(0xFFEFF6ED),
        onBackground = Color(0xFF1F2A22),
        surface = Color(0xFFF8FCF6),
        onSurface = Color(0xFF1F2A22),
        surfaceVariant = Color(0xFFDCE9DB),
        onSurfaceVariant = Color(0xFF435248),
        primaryContainer = Color(0xFFB9EBCB),
        onPrimaryContainer = Color(0xFF0A2B1B),
        secondaryContainer = Color(0xFFCFE9D7),
        onSecondaryContainer = Color(0xFF1A3224),
        tertiaryContainer = Color(0xFFDDEADA),
        onTertiaryContainer = Color(0xFF243126),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF8FD9AF),
        onPrimary = Color(0xFF0C3823),
        secondary = Color(0xFFB2D3BC),
        onSecondary = Color(0xFF1E392A),
        background = Color(0xFF182019),
        onBackground = Color(0xFFDCE8DB),
        surface = Color(0xFF1F2820),
        onSurface = Color(0xFFDCE8DB),
        surfaceVariant = Color(0xFF39463B),
        onSurfaceVariant = Color(0xFFBFCBBF),
        primaryContainer = Color(0xFF245236),
        onPrimaryContainer = Color(0xFFB9EBCB),
        secondaryContainer = Color(0xFF355441),
        onSecondaryContainer = Color(0xFFCFE9D7),
        tertiaryContainer = Color(0xFF3A4A3D),
        onTertiaryContainer = Color(0xFFDDEADA),
        errorContainer = Color(0xFF5A1C1C),
        onErrorContainer = Color(0xFFFFDAD6),
    )
)

private fun paletteOf(variant: ThemeVariant): ThemePalette = when (variant) {
    ThemeVariant.EyeCareNote -> eyeCarePalette
    ThemeVariant.BlueNote -> blueNotePalette
    ThemeVariant.GreenNote -> greenNotePalette
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    variant: ThemeVariant = ThemeVariant.EyeCareNote,
    content: @Composable () -> Unit
) {
    val palette = paletteOf(variant)
    val colorScheme = if (darkTheme) palette.dark else palette.light
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}