package com.chartanalyzer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Typography — HIG-inspired scale ─────────────────────────────────────────
//
// Apple HIG specifies a clear typographic hierarchy:
//   Large Title: 34pt / Regular  — screen-level headings
//   Title 1:     28pt / Regular  — section headings
//   Title 2:     22pt / Regular  — group headings
//   Title 3:     20pt / Regular  — sub-headings
//   Headline:    17pt / Semibold — emphasized body
//   Body:        17pt / Regular  — primary reading text
//   Callout:     16pt / Regular  — secondary body
//   Subheadline: 15pt / Regular  — supportive text
//   Footnote:    13pt / Regular  — small labels
//   Caption 1:   12pt / Regular  — captions / metadata
//   Caption 2:   11pt / Regular  — smallest readable text
//
// On Android with sp units (1sp ≈ 1pt on 1x density, matches Apple's points closely)

val HigTypography = Typography(
    // displayLarge  → Large Title
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.4).sp
    ),
    // displayMedium → Title 1
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    // displaySmall  → Title 2
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    // headlineLarge → Title 3
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp
    ),
    // headlineMedium → Headline (semibold)
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.4).sp
    ),
    // headlineSmall → Subheadline
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp
    ),
    // titleLarge → Headline bold variant
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.4).sp
    ),
    // titleMedium → Callout semibold
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.3).sp
    ),
    // titleSmall → Footnote medium
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp
    ),
    // bodyLarge → Body
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.4).sp
    ),
    // bodyMedium → Callout
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.2).sp
    ),
    // bodySmall → Subheadline
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp
    ),
    // labelLarge → Footnote semibold
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp
    ),
    // labelMedium → Caption 1
    labelMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    // labelSmall → Caption 2
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.sp
    )
)

// ─── Spacing tokens — HIG 8pt grid ───────────────────────────────────────────
//
// Apple HIG uses an 8-point spatial grid.
// All spacing should be multiples of 4dp (half-grid) or 8dp (full grid).

object HigSpacing {
    val xxxs: Dp = 2.dp   // hair gap
    val xxs:  Dp = 4.dp   // half-grid
    val xs:   Dp = 8.dp   // 1 unit
    val sm:   Dp = 12.dp  // 1.5 units
    val md:   Dp = 16.dp  // 2 units — standard content padding
    val lg:   Dp = 20.dp  // 2.5 units
    val xl:   Dp = 24.dp  // 3 units
    val xxl:  Dp = 32.dp  // 4 units
    val xxxl: Dp = 44.dp  // minimum touch target per HIG
    val section: Dp = 20.dp  // standard section spacing
    val contentPadding: Dp = 16.dp  // standard horizontal content padding
    val listItemHeight: Dp = 44.dp  // minimum list row height per HIG
}

// ─── Shape tokens — HIG continuous corner radius ─────────────────────────────
//
// Apple HIG uses continuous ("squircle") corner curves throughout iOS.
// On Android we approximate with RoundedCornerShape at these radii:
//   Small interactive elements: 8dp
//   Cards/groups:              12dp
//   Sheets/large surfaces:     16dp
//   Chips/badges:               6dp
//   Fully rounded (pills):     50%

object HigShapes {
    val extraSmall = 6.dp    // badges, chips
    val small      = 8.dp    // buttons, text fields
    val medium     = 12.dp   // cards, cells
    val large      = 16.dp   // modals, sheets
    val extraLarge = 20.dp   // full-bleed surfaces
}

// ─── Elevation tokens — HIG depth layers ─────────────────────────────────────
//
// Apple HIG uses blur-backed translucency rather than hard shadows.
// On Android we map this to elevation levels that cast soft shadows.

object HigElevation {
    val none:    Dp = 0.dp    // flat cells
    val low:     Dp = 1.dp    // subtle card lift
    val medium:  Dp = 2.dp    // standard card
    val high:    Dp = 4.dp    // modal/action sheet
    val overlay: Dp = 8.dp    // floating action elements
}
