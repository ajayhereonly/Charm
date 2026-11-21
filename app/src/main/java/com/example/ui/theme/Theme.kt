package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PyForgeDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnCyan,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = PythonAmber,
    onSecondary = OnAmber,
    secondaryContainer = DeepAmberContainer,
    onSecondaryContainer = PythonAmber,
    tertiary = SyntaxViolet,
    onTertiary = ObsidianBg,
    tertiaryContainer = DeepVioletContainer,
    onTertiaryContainer = SyntaxViolet,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1E293B),
    error = ErrorCoral,
    onError = ObsidianBg,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorCoral
)

private val PyForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PyForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PyForgeDarkColorScheme,
        typography = Typography,
        shapes = PyForgeShapes,
        content = content
    )
}
