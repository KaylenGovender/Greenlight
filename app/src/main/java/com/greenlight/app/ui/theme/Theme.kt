package com.greenlight.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Greenlight: "Electric Emerald & Charcoal". Always dark.
val Carbon = Color(0xFF111318)      // background
val Zinc = Color(0xFF1E222B)        // cards
val ZincHigh = Color(0xFF262B36)    // raised cards, chips
val ZincLine = Color(0xFF343A46)    // outlines
val Emerald = Color(0xFF10B981)     // primary: agreement, matches, progress
val Mint = Color(0xFF00E599)        // bright highlight
val EmeraldDeep = Color(0xFF064E3B) // gradients
val Amber = Color(0xFFF59E0B)       // ratings, badges
val OffWhite = Color(0xFFF9FAFB)
val Muted = Color(0xFF9CA3AF)

/** Card scrim: keeps white text readable over bright photos. */
val ScrimEnd = Color(0xF20F0A14)

val PassColor = Color(0xFF4B5563)
val InterestedColor = Emerald

private val Colors = darkColorScheme(
    primary = Emerald,
    onPrimary = Color(0xFF04110B),
    primaryContainer = Color(0xFF0B3B2C),
    onPrimaryContainer = Color(0xFFB7F5DC),
    secondary = Mint,
    onSecondary = Color(0xFF04110B),
    secondaryContainer = ZincHigh,
    onSecondaryContainer = OffWhite,
    tertiary = Amber,
    onTertiary = Color(0xFF1F1300),
    tertiaryContainer = Color(0xFF3D2A05),
    onTertiaryContainer = Color(0xFFFDE3B5),
    background = Carbon,
    onBackground = OffWhite,
    surface = Carbon,
    onSurface = OffWhite,
    surfaceVariant = Zinc,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Carbon,
    surfaceContainerLow = Color(0xFF181B22),
    surfaceContainer = Zinc,
    surfaceContainerHigh = ZincHigh,
    surfaceContainerHighest = Color(0xFF2E3440),
    outline = ZincLine,
    outlineVariant = Color(0xFF2A2F3A),
    error = Color(0xFFF87171),
    onError = Color(0xFF2B0606),
    errorContainer = Color(0xFF3B1414),
    onErrorContainer = Color(0xFFFECACA),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
)

val StampStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)

@Composable
fun GreenlightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = AppShapes, typography = AppTypography, content = content)
}
