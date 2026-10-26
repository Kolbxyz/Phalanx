package org.phalanx.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.phalanx.android.R

/*
 * One bundled variable font (Google Sans Flex, OFL) serves both families. It is bundled rather than
 * fetched through Google's downloadable-fonts provider: that provider is a Play services
 * dependency, which this app does not have, and it cannot dial the roundness axis per style.
 */

/** How far up the font's 0–100 `ROND` axis titles sit; full roundness on bold weights eats letterforms. */
private const val TITLE_ROUNDNESS = 65f

private fun flexFont(weight: FontWeight, roundness: Float) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", roundness)
    )
)

private val weights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

internal val PhalanxBodyFontFamily = FontFamily(weights.map { flexFont(it, roundness = 0f) })

internal val PhalanxRoundedFontFamily = FontFamily(weights.map { flexFont(it, TITLE_ROUNDNESS) })

/**
 * Compose trims the line box to the requested `lineHeight` by default, and Material's `Tab`
 * positions a label by its last baseline inside a fixed slot; between them tall descenders get
 * clipped. Centring the glyphs and trimming nothing keeps every tail intact.
 */
private val FullLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun title(weight: FontWeight, size: TextUnit, line: TextUnit, spacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = PhalanxRoundedFontFamily,
    lineHeightStyle = FullLineHeight,
    fontWeight = weight,
    fontSize = size,
    lineHeight = line,
    letterSpacing = spacing
)

private fun body(weight: FontWeight, size: TextUnit, line: TextUnit, spacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = PhalanxBodyFontFamily,
    lineHeightStyle = FullLineHeight,
    fontWeight = weight,
    fontSize = size,
    lineHeight = line,
    letterSpacing = spacing
)

internal val PhalanxTypography = Typography(
    displayLarge = title(FontWeight.ExtraBold, 54.sp, 60.sp, (-1).sp),
    headlineLarge = title(FontWeight.Bold, 32.sp, 40.sp, (-0.5).sp),
    headlineMedium = title(FontWeight.Bold, 26.sp, 34.sp, (-0.2).sp),
    headlineSmall = title(FontWeight.Bold, 22.sp, 28.sp),
    titleLarge = title(FontWeight.SemiBold, 20.sp, 26.sp),
    titleMedium = title(FontWeight.SemiBold, 16.sp, 22.sp, 0.1.sp),
    titleSmall = title(FontWeight.Medium, 14.sp, 20.sp, 0.1.sp),
    bodyLarge = body(FontWeight.Normal, 16.sp, 24.sp, 0.3.sp),
    bodyMedium = body(FontWeight.Normal, 14.sp, 20.sp, 0.2.sp),
    bodySmall = body(FontWeight.Normal, 12.sp, 16.sp),
    labelLarge = body(FontWeight.SemiBold, 14.sp, 20.sp, 0.1.sp),
    labelMedium = body(FontWeight.Medium, 12.sp, 16.sp, 0.4.sp),
    labelSmall = body(FontWeight.SemiBold, 10.sp, 14.sp, 0.5.sp)
)
