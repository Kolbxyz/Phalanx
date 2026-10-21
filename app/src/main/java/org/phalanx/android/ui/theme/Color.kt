package org.phalanx.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Fallback palette for devices without Monet dynamic colour (below API 31, or when the user turns
 * it off). Seeded from a steel-blue and bronze pair: the shield and the metal it is made of.
 */

internal val PhalanxLightScheme = lightColorScheme(
    primary = Color(0xFF2B5C93),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF545F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBD),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3F8),
    surfaceContainer = Color(0xFFEBEEF3),
    surfaceContainerHigh = Color(0xFFE5E8ED),
    surfaceContainerHighest = Color(0xFFE0E2E7),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceBright = Color(0xFFF7F9FC),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF)
)

internal val PhalanxDarkScheme = darkColorScheme(
    primary = Color(0xFFA3C9FF),
    onPrimary = Color(0xFF00315B),
    primaryContainer = Color(0xFF0D4579),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBD),
    background = Color(0xFF10151C),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF10151C),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0B1017),
    surfaceContainerLow = Color(0xFF181C22),
    surfaceContainer = Color(0xFF1C2026),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),
    surfaceDim = Color(0xFF10151C),
    surfaceBright = Color(0xFF363A41),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E)
)
