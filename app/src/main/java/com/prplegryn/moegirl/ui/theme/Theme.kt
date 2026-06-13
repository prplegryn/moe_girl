package com.prplegryn.moegirl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF315CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF087E72),
    onSecondary = Color.White,
    tertiary = Color(0xFFC04767),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF161A22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161A22),
    surfaceVariant = Color(0xFFE7ECF3),
    onSurfaceVariant = Color(0xFF596171),
    outline = Color(0xFFD8DEE8),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9AADFF),
    onPrimary = Color(0xFF0C1B58),
    secondary = Color(0xFF5AD8C3),
    onSecondary = Color(0xFF062A25),
    tertiary = Color(0xFFFFA2B8),
    background = Color(0xFF101218),
    onBackground = Color(0xFFF2F5FA),
    surface = Color(0xFF1A1D25),
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = Color(0xFF252A35),
    onSurfaceVariant = Color(0xFFC4CAD6),
    outline = Color(0xFF394150),
)

private val MoeTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun MoeGirlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = MoeTypography,
        content = content,
    )
}

