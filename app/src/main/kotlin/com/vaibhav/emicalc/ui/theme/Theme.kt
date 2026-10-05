package com.vaibhav.emicalc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A calm, slightly cool palette. Money screens are read when people are anxious, so the
// accent is reserved for the figure that matters and red is kept strictly for amounts
// the user owes rather than for general emphasis.
private val Teal = Color(0xFF00695C)
private val TealLight = Color(0xFF4DB6AC)
private val Sand = Color(0xFFF5F2EC)
private val Ink = Color(0xFF14181A)
private val Slate = Color(0xFF42524F)
private val Danger = Color(0xFFB3261E)
private val DangerLight = Color(0xFFFFB4AB)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Slate,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6E2DB),
    onSurfaceVariant = Slate,
    error = Danger,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005044),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFB0CCC7),
    onSecondary = Color(0xFF1C3530),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE2E3E0),
    surface = Color(0xFF181D1C),
    onSurface = Color(0xFFE2E3E0),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    error = DangerLight,
    onError = Color(0xFF690005),
)

/**
 * Typography leans on tabular-friendly weights for figures. Amounts are rendered with
 * [FontFamily.Monospace] in the components that show them so digits line up down a
 * column, which matters when comparing an amortisation schedule against a statement.
 */
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun EmiCalcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You colours, where the device offers them. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
