package com.beremi.cameragyroacccapture.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Copper,
    onPrimary = Ink,
    primaryContainer = Slate,
    onPrimaryContainer = Mist,
    secondary = Sea,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF173748),
    onSecondaryContainer = Mist,
    background = Ink,
    onBackground = Mist,
    surface = Color(0xFF0B151D),
    onSurface = Mist,
    surfaceVariant = Slate,
    onSurfaceVariant = Color(0xFFCCD7DE),
    error = Color(0xFFFFB4A8),
)

private val LightColors = lightColorScheme(
    primary = Copper,
    onPrimary = Ink,
    primaryContainer = Color(0xFFF2E2D2),
    onPrimaryContainer = Ink,
    secondary = Sea,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFD8EBF2),
    onSecondaryContainer = Ink,
    background = Color(0xFFF4F6F8),
    onBackground = Ink,
    surface = Color(0xFFFBFCFD),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7EDF1),
    onSurfaceVariant = Slate,
    error = Ember,
)

private val AppTypography = androidx.compose.material3.Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun CameraGyroAccCaptureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
