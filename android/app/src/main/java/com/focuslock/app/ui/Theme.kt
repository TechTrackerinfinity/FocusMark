package com.focuslock.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FocusMarkPalette {
    val Lime = Color(0xFFDAF5A4)
    val Forest = Color(0xFF203A2A)
    val YouTube = Color(0xFFE55F50)
    val Instagram = Color(0xFFB970A5)
}

private val DarkColors = darkColorScheme(
    primary = FocusMarkPalette.Lime,
    onPrimary = FocusMarkPalette.Forest,
    primaryContainer = Color(0xFF2A432E),
    onPrimaryContainer = Color(0xFFE0F4CE),
    secondary = Color(0xFFAAC5A2),
    onSecondary = Color(0xFF172D1B),
    secondaryContainer = Color(0xFF263B2B),
    onSecondaryContainer = Color(0xFFD8EBD2),
    background = Color(0xFF101712),
    onBackground = Color(0xFFF0F4EC),
    surface = Color(0xFF19221C),
    onSurface = Color(0xFFF0F4EC),
    surfaceVariant = Color(0xFF243127),
    onSurfaceVariant = Color(0xFFABB8AA),
    outline = Color(0xFF758372),
    outlineVariant = Color(0xFF344237),
    error = Color(0xFFFFB4A8),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF315B38),
    onPrimary = Color.White,
    primaryContainer = FocusMarkPalette.Lime,
    onPrimaryContainer = FocusMarkPalette.Forest,
    secondary = Color(0xFF4F6B51),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EDDA),
    onSecondaryContainer = FocusMarkPalette.Forest,
    background = Color(0xFFF5F7F0),
    onBackground = Color(0xFF1B2A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2A1F),
    surfaceVariant = Color(0xFFE8EEE2),
    onSurfaceVariant = Color(0xFF63705F),
    outline = Color(0xFF7D8977),
    outlineVariant = Color(0xFFDDE5D6),
    error = Color(0xFFAB3628),
)

private val FocusMarkTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun FocusMarkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FocusMarkTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
