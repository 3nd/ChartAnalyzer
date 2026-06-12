package com.chartanalyzer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Apple HIG Semantic Color Palette ─────────────────────────────────────────
// Reference: developer.apple.com/design/human-interface-guidelines/color

object AppleColors {
    // ── System Blues (primary interactive) ───────────────────────────────────
    val systemBlue        = Color(0xFF007AFF)  // light
    val systemBlueDark    = Color(0xFF0A84FF)  // dark

    // ── System Greens (success, buy signals) ─────────────────────────────────
    val systemGreen       = Color(0xFF34C759)
    val systemGreenDark   = Color(0xFF30D158)

    // ── System Reds (destructive, sell signals) ───────────────────────────────
    val systemRed         = Color(0xFFFF3B30)
    val systemRedDark     = Color(0xFFFF453A)

    // ── System Oranges (warnings, moderate sells) ────────────────────────────
    val systemOrange      = Color(0xFFFF9500)
    val systemOrangeDark  = Color(0xFFFF9F0A)

    // ── System Yellows (caution) ─────────────────────────────────────────────
    val systemYellow      = Color(0xFFFFCC00)
    val systemYellowDark  = Color(0xFFFFD60A)

    // ── System Purples (ML / confidence) ─────────────────────────────────────
    val systemPurple      = Color(0xFFAF52DE)
    val systemPurpleDark  = Color(0xFFBF5AF2)

    // ── System Teals (secondary) ─────────────────────────────────────────────
    val systemTeal        = Color(0xFF5AC8FA)
    val systemTealDark    = Color(0xFF64D2FF)

    // ── System Indigo ────────────────────────────────────────────────────────
    val systemIndigo      = Color(0xFF5856D6)
    val systemIndigoDark  = Color(0xFF5E5CE6)

    // ── System Grays ─────────────────────────────────────────────────────────
    val systemGray        = Color(0xFF8E8E93)
    val systemGray2       = Color(0xFFAEAEB2)
    val systemGray3       = Color(0xFFC7C7CC)
    val systemGray4       = Color(0xFFD1D1D6)
    val systemGray5       = Color(0xFFE5E5EA)
    val systemGray6       = Color(0xFFF2F2F7)
    val systemGrayDark    = Color(0xFF636366)
    val systemGray2Dark   = Color(0xFF48484A)
    val systemGray3Dark   = Color(0xFF3A3A3C)
    val systemGray4Dark   = Color(0xFF2C2C2E)
    val systemGray5Dark   = Color(0xFF1C1C1E)
    val systemGray6Dark   = Color(0xFF111113)

    // ── Background colors ────────────────────────────────────────────────────
    val systemBackground        = Color(0xFFFFFFFF)
    val systemBackgroundDark    = Color(0xFF000000)
    val secondarySystemBackground     = Color(0xFFF2F2F7)
    val secondarySystemBackgroundDark = Color(0xFF1C1C1E)
    val tertiarySystemBackground      = Color(0xFFFFFFFF)
    val tertiarySystemBackgroundDark  = Color(0xFF2C2C2E)

    // ── Grouped backgrounds (for inset grouped lists like Settings) ──────────
    val systemGroupedBackground       = Color(0xFFF2F2F7)
    val systemGroupedBackgroundDark   = Color(0xFF000000)
    val secondaryGroupedBackground    = Color(0xFFFFFFFF)
    val secondaryGroupedBackgroundDark= Color(0xFF1C1C1E)
    val tertiaryGroupedBackground     = Color(0xFFF2F2F7)
    val tertiaryGroupedBackgroundDark = Color(0xFF2C2C2E)

    // ── Label colors ─────────────────────────────────────────────────────────
    val label               = Color(0xFF000000)
    val labelDark           = Color(0xFFFFFFFF)
    val secondaryLabel      = Color(0xFF3C3C43).copy(alpha = 0.60f)
    val secondaryLabelDark  = Color(0xFFEBEBF5).copy(alpha = 0.60f)
    val tertiaryLabel       = Color(0xFF3C3C43).copy(alpha = 0.30f)
    val tertiaryLabelDark   = Color(0xFFEBEBF5).copy(alpha = 0.30f)
    val quaternaryLabel     = Color(0xFF3C3C43).copy(alpha = 0.18f)
    val quaternaryLabelDark = Color(0xFFEBEBF5).copy(alpha = 0.16f)

    // ── Separator colors ─────────────────────────────────────────────────────
    val separator           = Color(0xFF3C3C43).copy(alpha = 0.29f)
    val separatorDark       = Color(0xFF545458).copy(alpha = 0.65f)
    val opaqueSeparator     = Color(0xFFC6C6C8)
    val opaqueSeparatorDark = Color(0xFF38383A)

    // ── Fill colors ──────────────────────────────────────────────────────────
    val systemFill           = Color(0xFF787880).copy(alpha = 0.20f)
    val systemFillDark       = Color(0xFF787880).copy(alpha = 0.36f)
    val secondaryFill        = Color(0xFF787880).copy(alpha = 0.16f)
    val secondaryFillDark    = Color(0xFF787880).copy(alpha = 0.32f)
    val tertiaryFill         = Color(0xFF767680).copy(alpha = 0.12f)
    val tertiaryFillDark     = Color(0xFF767680).copy(alpha = 0.24f)
    val quaternaryFill       = Color(0xFF747480).copy(alpha = 0.08f)
    val quaternaryFillDark   = Color(0xFF747480).copy(alpha = 0.18f)
}

// ─── Compose color scheme — Apple HIG semantics mapped to Material3 roles ────

val AppleLightColors = lightColorScheme(
    primary              = AppleColors.systemBlue,
    onPrimary            = Color.White,
    primaryContainer     = AppleColors.systemBlue.copy(alpha = 0.12f),
    onPrimaryContainer   = AppleColors.systemBlue,
    secondary            = AppleColors.systemGray,
    onSecondary          = Color.White,
    secondaryContainer   = AppleColors.systemGray5,
    onSecondaryContainer = AppleColors.label,
    tertiary             = AppleColors.systemPurple,
    onTertiary           = Color.White,
    tertiaryContainer    = AppleColors.systemPurple.copy(alpha = 0.10f),
    onTertiaryContainer  = AppleColors.systemPurple,
    error                = AppleColors.systemRed,
    onError              = Color.White,
    errorContainer       = AppleColors.systemRed.copy(alpha = 0.10f),
    onErrorContainer     = AppleColors.systemRed,
    background           = AppleColors.systemGroupedBackground,
    onBackground         = AppleColors.label,
    surface              = AppleColors.systemBackground,
    onSurface            = AppleColors.label,
    surfaceVariant       = AppleColors.secondarySystemBackground,
    onSurfaceVariant     = Color(0xFF3C3C43).copy(alpha = 0.60f),
    outline              = AppleColors.opaqueSeparator,
    outlineVariant       = AppleColors.separator,
    scrim                = Color(0x99000000),
    inverseSurface       = AppleColors.systemGray5Dark,
    inverseOnSurface     = AppleColors.labelDark,
    inversePrimary       = AppleColors.systemBlueDark
)

val AppleDarkColors = darkColorScheme(
    primary              = AppleColors.systemBlueDark,
    onPrimary            = Color.Black,
    primaryContainer     = AppleColors.systemBlueDark.copy(alpha = 0.15f),
    onPrimaryContainer   = AppleColors.systemBlueDark,
    secondary            = AppleColors.systemGrayDark,
    onSecondary          = Color.White,
    secondaryContainer   = AppleColors.systemGray4Dark,
    onSecondaryContainer = AppleColors.labelDark,
    tertiary             = AppleColors.systemPurpleDark,
    onTertiary           = Color.Black,
    tertiaryContainer    = AppleColors.systemPurpleDark.copy(alpha = 0.15f),
    onTertiaryContainer  = AppleColors.systemPurpleDark,
    error                = AppleColors.systemRedDark,
    onError              = Color.Black,
    errorContainer       = AppleColors.systemRedDark.copy(alpha = 0.15f),
    onErrorContainer     = AppleColors.systemRedDark,
    background           = AppleColors.systemGroupedBackgroundDark,
    onBackground         = AppleColors.labelDark,
    surface              = AppleColors.systemGray6Dark,
    onSurface            = AppleColors.labelDark,
    surfaceVariant       = AppleColors.secondarySystemBackgroundDark,
    onSurfaceVariant     = Color(0xFFEBEBF5).copy(alpha = 0.60f),
    outline              = AppleColors.opaqueSeparatorDark,
    outlineVariant       = AppleColors.separatorDark,
    scrim                = Color(0x99000000),
    inverseSurface       = AppleColors.systemGray5,
    inverseOnSurface     = AppleColors.label,
    inversePrimary       = AppleColors.systemBlue
)

// ─── Apple HIG Typography Scale ───────────────────────────────────────────────
// Reference: developer.apple.com/design/human-interface-guidelines/typography
// Using system default (matches SF Pro on iOS devices, Roboto elsewhere)

val AppleTypography = Typography(
    // Large Title  — 34sp, Regular (used for top-level navigation bar titles when scrolled up)
    displayLarge  = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 41.sp, letterSpacing = 0.37.sp),
    // Title 1      — 28sp
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, letterSpacing = 0.sp),
    // Title 2      — 22sp
    displaySmall  = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, letterSpacing = 0.35.sp),
    // Title 3      — 20sp
    headlineLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp, letterSpacing = 0.38.sp),
    // Headline     — 17sp Semibold
    headlineMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = (-0.41).sp),
    // Body          — 17sp Regular
    headlineSmall  = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, letterSpacing = (-0.41).sp),
    // Callout      — 16sp Regular
    titleLarge    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp, letterSpacing = (-0.32).sp),
    // Subheadline  — 15sp Regular
    titleMedium   = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = (-0.24).sp),
    // Footnote     — 13sp Regular
    titleSmall    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp, letterSpacing = (-0.08).sp),
    // Caption 1    — 12sp Regular
    bodyLarge     = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.sp),
    // Caption 2    — 11sp Regular
    bodyMedium    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 13.sp, letterSpacing = 0.07.sp),
    // Body (standard)
    bodySmall     = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, letterSpacing = (-0.41).sp),
    // Label large
    labelLarge    = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = (-0.41).sp),
    // Label medium
    labelMedium   = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp, letterSpacing = (-0.08).sp),
    // Label small
    labelSmall    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 13.sp, letterSpacing = 0.07.sp)
)

// ─── Apple HIG Spacing System ────────────────────────────────────────────────

object AppleSpacing {
    val xs:   Dp = 4.dp
    val sm:   Dp = 8.dp
    val md:   Dp = 12.dp
    val base: Dp = 16.dp   // standard horizontal margin
    val lg:   Dp = 20.dp
    val xl:   Dp = 24.dp
    val xxl:  Dp = 32.dp
    val xxxl: Dp = 44.dp   // minimum touch target
}

// ─── Apple HIG Shape System ───────────────────────────────────────────────────

object AppleShapes {
    val none:    Dp = 0.dp
    val xs:      Dp = 6.dp    // small controls (chips, badges)
    val sm:      Dp = 10.dp   // buttons, text fields
    val md:      Dp = 12.dp   // cards, list rows
    val lg:      Dp = 16.dp   // sheets, large cards
    val xl:      Dp = 20.dp   // bottom sheets
    val full:    Dp = 100.dp  // pills, circular elements
}

// ─── Composable helpers ───────────────────────────────────────────────────────

val LocalIsDarkTheme = compositionLocalOf { false }

// Provides semantic iOS color shortcuts for use in composables
@Composable
fun appleBlue()   = if (isSystemInDarkTheme()) AppleColors.systemBlueDark   else AppleColors.systemBlue
@Composable
fun appleGreen()  = if (isSystemInDarkTheme()) AppleColors.systemGreenDark  else AppleColors.systemGreen
@Composable
fun appleRed()    = if (isSystemInDarkTheme()) AppleColors.systemRedDark    else AppleColors.systemRed
@Composable
fun appleOrange() = if (isSystemInDarkTheme()) AppleColors.systemOrangeDark else AppleColors.systemOrange
@Composable
fun appleYellow() = if (isSystemInDarkTheme()) AppleColors.systemYellowDark else AppleColors.systemYellow
@Composable
fun applePurple() = if (isSystemInDarkTheme()) AppleColors.systemPurpleDark else AppleColors.systemPurple
@Composable
fun appleTeal()   = if (isSystemInDarkTheme()) AppleColors.systemTealDark   else AppleColors.systemTeal
@Composable
fun appleGray()   = if (isSystemInDarkTheme()) AppleColors.systemGrayDark   else AppleColors.systemGray
@Composable
fun appleLabel()  = if (isSystemInDarkTheme()) AppleColors.labelDark        else AppleColors.label
@Composable
fun appleSecondaryLabel() = if (isSystemInDarkTheme()) AppleColors.secondaryLabelDark else AppleColors.secondaryLabel
@Composable
fun appleSeparator() = if (isSystemInDarkTheme()) AppleColors.separatorDark else AppleColors.separator
@Composable
fun appleGroupedBackground() = if (isSystemInDarkTheme()) AppleColors.systemGroupedBackgroundDark else AppleColors.systemGroupedBackground
@Composable
fun appleSecondaryGroupedBackground() = if (isSystemInDarkTheme()) AppleColors.secondaryGroupedBackgroundDark else AppleColors.secondaryGroupedBackground
@Composable
fun appleFill() = if (isSystemInDarkTheme()) AppleColors.systemFillDark else AppleColors.systemFill
