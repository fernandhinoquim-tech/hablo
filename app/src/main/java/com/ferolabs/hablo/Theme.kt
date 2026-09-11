package com.ferolabs.hablo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Cream = Color(0xFFFDF9F4)
val Ink = Color(0xFF201C19)
val InkSoft = Color(0xFF6B625B)
val Line = Color(0xFFE8E0D7)
val GoodGreen = Color(0xFF2E7D51)
val GoodGreenSoft = Color(0xFFE6F4EC)
val BadRed = Color(0xFFB3372C)
val BadRedSoft = Color(0xFFFBEAE8)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun HabloTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = accent,
        onSecondary = Color.White,
        background = Cream,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Color(0xFFF3EDE6),
        onSurfaceVariant = InkSoft,
        outline = Line,
        error = BadRed,
        onError = Color.White
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}
