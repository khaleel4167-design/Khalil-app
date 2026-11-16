package com.khalil.noor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.khalil.noor.R

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)
val Amiri = FontFamily(Font(R.font.amiri_bold, FontWeight.Bold))

private val Light = lightColorScheme(
    primary = Color(0xFF1F6B52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5EADF),
    onPrimaryContainer = Color(0xFF0B2E25),
    secondary = Color(0xFFA9783A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E2C4),
    onSecondaryContainer = Color(0xFF3E2A18),
    background = Color(0xFFFBF7F0),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFF1EADF),
    onSurfaceVariant = Color(0xFF5B5246),
    outline = Color(0xFFD9CCB8),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FCBAA),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF134A3B),
    onPrimaryContainer = Color(0xFFD5EADF),
    secondary = Color(0xFFE3BE6E),
    onSecondary = Color(0xFF3E2A18),
    secondaryContainer = Color(0xFF4A3920),
    onSecondaryContainer = Color(0xFFF3E2C4),
    background = Color(0xFF12140F),
    onBackground = Color(0xFFECE6DA),
    surface = Color(0xFF1A1C17),
    onSurface = Color(0xFFECE6DA),
    surfaceVariant = Color(0xFF26281F),
    onSurfaceVariant = Color(0xFFC9C0B0),
    outline = Color(0xFF4A4A3E),
    error = Color(0xFFF2B8B5),
)

private fun t(size: Int, w: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Tajawal, fontSize = size.sp, fontWeight = w, lineHeight = (size * 1.45).sp)

private val Type = Typography(
    headlineMedium = t(26, FontWeight.Bold),
    titleLarge = t(21, FontWeight.Bold),
    titleMedium = t(17, FontWeight.Bold),
    titleSmall = t(15, FontWeight.Medium),
    bodyLarge = t(16),
    bodyMedium = t(14),
    bodySmall = t(12),
    labelLarge = t(15, FontWeight.Bold),
    labelMedium = t(13, FontWeight.Medium),
    labelSmall = t(11, FontWeight.Medium),
)

@Composable
fun NoorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
