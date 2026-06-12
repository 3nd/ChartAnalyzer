package com.chartanalyzer.app.ui.theme

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── HIG Section Header ───────────────────────────────────────────────────────
// Like iOS Settings section headers: uppercase, secondary label color, 16dp left inset

@Composable
fun HigSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    footnote: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.xs)
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = appleSecondaryLabel(),
            letterSpacing = 0.5.sp
        )
        footnote?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = appleSecondaryLabel())
        }
    }
}

// ─── HIG Inset Grouped Card (like iOS table view inset section) ───────────────

@Composable
fun HigGroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpacing.base),
        shape = RoundedCornerShape(AppleShapes.md),
        colors = CardDefaults.cardColors(
            containerColor = appleSecondaryGroupedBackground()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

// ─── HIG List Row ─────────────────────────────────────────────────────────────
// Standard iOS table view row with leading icon, title, subtitle, trailing accessory

@Composable
fun HigListRow(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconColor: Color = appleBlue(),
    leadingIconBackground: Color = leadingIconColor.copy(alpha = 0.12f),
    subtitle: String? = null,
    trailingText: String? = null,
    trailingValue: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .heightIn(min = AppleSpacing.xxxl)  // 44dp minimum touch target
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md)
    ) {
        // Leading icon (iOS-style rounded square icon)
        leadingIcon?.let { icon ->
            Box(
                modifier = Modifier
                    .size(29.dp)
                    .clip(RoundedCornerShape(AppleShapes.xs))
                    .background(leadingIconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    modifier = Modifier.size(17.dp),
                    tint = leadingIconColor)
            }
        }

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.headlineSmall,
                color = appleLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it,
                    style = MaterialTheme.typography.titleMedium,
                    color = appleSecondaryLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }

        // Trailing
        trailingText?.let {
            Text(it,
                style = MaterialTheme.typography.headlineSmall,
                color = appleSecondaryLabel())
        }
        trailingValue?.invoke()
        if (showChevron) {
            Icon(Icons.Filled.ChevronRight, null,
                modifier = Modifier.size(20.dp),
                tint = appleGray().copy(alpha = 0.5f))
        }
    }
}

// ─── HIG Row Divider (inset, like iOS table view separator) ──────────────────

@Composable
fun HigDivider(startInset: Dp = AppleSpacing.base) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(0.5.dp)
        .padding(start = startInset)
        .background(appleSeparator()))
}

// ─── HIG Primary Button ───────────────────────────────────────────────────────
// Full-width rounded pill button — iOS filled button style

@Composable
fun HigPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = appleBlue(),
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(AppleSpacing.xxxl),  // 44dp — Apple minimum touch target
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(AppleShapes.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = appleGray().copy(alpha = 0.20f),
            disabledContentColor = appleGray()
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(AppleSpacing.sm))
        } else {
            leadingIcon?.let {
                Icon(it, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppleSpacing.sm))
            }
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

// ─── HIG Secondary Button (tinted/gray fill) ─────────────────────────────────

@Composable
fun HigSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = appleBlue()
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(AppleSpacing.xxxl),
        enabled = enabled,
        shape = RoundedCornerShape(AppleShapes.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.12f),
            contentColor = color,
            disabledContainerColor = appleGray().copy(alpha = 0.10f),
            disabledContentColor = appleGray()
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

// ─── HIG Destructive Button (red, like iOS delete) ───────────────────────────

@Composable
fun HigDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(AppleSpacing.xxxl),
        colors = ButtonDefaults.textButtonColors(contentColor = appleRed())
    ) {
        Text(text, fontWeight = FontWeight.Normal, fontSize = 17.sp)
    }
}

// ─── HIG Badge ───────────────────────────────────────────────────────────────
// iOS-style numeric or text badge

@Composable
fun HigBadge(
    text: String,
    color: Color = appleRed(),
    textColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppleShapes.full),
        color = color
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

// ─── HIG Signal Badge ─────────────────────────────────────────────────────────
// Color-coded signal indicator with iOS pill style

@Composable
fun HigSignalBadge(
    label: String,
    color: Color,
    textColor: Color = if (color.red > 0.7f && color.green > 0.7f) Color.Black else Color.White,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppleShapes.xs),
        color = color
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            letterSpacing = 0.3.sp
        )
    }
}

// ─── HIG Stat Cell ────────────────────────────────────────────────────────────
// iOS-style metric card (like Apple Fitness rings detail cells)

@Composable
fun HigStatCell(
    label: String,
    value: String,
    valueColor: Color = appleLabel(),
    subLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppleShapes.sm))
            .background(appleSecondaryGroupedBackground())
            .padding(horizontal = AppleSpacing.md, vertical = AppleSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 11.sp, color = appleSecondaryLabel(),
            textAlign = TextAlign.Center, maxLines = 1)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = valueColor, textAlign = TextAlign.Center, maxLines = 1)
        subLabel?.let {
            Text(it, fontSize = 10.sp, color = appleSecondaryLabel(),
                textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

// ─── HIG Probability Bar ──────────────────────────────────────────────────────
// iOS-style progress bar with label and value

@Composable
fun HigProbabilityBar(
    label: String,
    value: Double,
    color: Color,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = appleSecondaryLabel())
            if (showPercent) {
                Text("${"%.0f".format(value)}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium, color = color)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(AppleShapes.full))
                .background(appleGray().copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (value / 100.0).coerceIn(0.0, 1.0).toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(AppleShapes.full))
                    .background(color)
            )
        }
    }
}

// ─── HIG Switch Row ───────────────────────────────────────────────────────────

@Composable
fun HigSwitchRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppleSpacing.xxxl)
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = appleLabel())
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = appleSecondaryLabel())
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = appleGreen(),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = appleGray().copy(alpha = 0.25f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ─── HIG Slider Row ───────────────────────────────────────────────────────────

@Composable
fun HigSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onValue: (Float) -> Unit,
    valueFormatter: (Float) -> String = { "%.1f".format(it) },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = appleLabel())
            Text(valueFormatter(value),
                style = MaterialTheme.typography.titleMedium, color = appleBlue())
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = min..max,
            modifier = Modifier.height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = appleBlue(),
                inactiveTrackColor = appleGray().copy(alpha = 0.25f)
            )
        )
    }
}

// ─── HIG Info Row (label + value, like iOS detail cell) ──────────────────────

@Composable
fun HigInfoRow(
    label: String,
    value: String,
    valueColor: Color = appleLabel(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, color = appleLabel())
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
    }
}

// ─── HIG Empty State ──────────────────────────────────────────────────────────

@Composable
fun HigEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppleSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppleSpacing.sm)
    ) {
        Icon(icon, null,
            modifier = Modifier.size(56.dp),
            tint = appleGray().copy(alpha = 0.35f))
        Spacer(Modifier.height(AppleSpacing.xs))
        Text(title,
            style = MaterialTheme.typography.headlineMedium,
            color = appleLabel(),
            textAlign = TextAlign.Center)
        subtitle?.let {
            Text(it,
                style = MaterialTheme.typography.titleMedium,
                color = appleSecondaryLabel(),
                textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(AppleSpacing.sm))
            HigSecondaryButton(actionLabel, onAction,
                modifier = Modifier.widthIn(min = 140.dp))
        }
    }
}

// ─── HIG Loading View ─────────────────────────────────────────────────────────

@Composable
fun HigLoadingView(message: String = "Loading...", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppleSpacing.md)) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
                color = appleBlue()
            )
            Text(message,
                style = MaterialTheme.typography.titleMedium,
                color = appleSecondaryLabel())
        }
    }
}

// ─── HIG Page Title ───────────────────────────────────────────────────────────
// iOS Large Title navigation style

@Composable
fun HigLargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
        style = MaterialTheme.typography.displayLarge,
        color = appleLabel()
    )
}

// ─── HIG Tag/Filter Chip ──────────────────────────────────────────────────────

@Composable
fun HigFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = appleBlue()
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(AppleShapes.full),
        color = if (selected) selectedColor else appleGray().copy(alpha = 0.12f),
        contentColor = if (selected) Color.White else appleSecondaryLabel()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 14.dp),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// ─── HIG Disclosure Group (expandable section like iOS) ──────────────────────

@Composable
fun HigDisclosureGroup(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    iconColor: Color = appleBlue(),
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        HigListRow(
            title = title,
            subtitle = subtitle,
            leadingIcon = leadingIcon,
            leadingIconColor = iconColor,
            showChevron = false,
            trailingValue = {
                Icon(
                    if (expanded) Icons.Filled.ChevronRight else Icons.Filled.ChevronRight,
                    null,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsRotation(if (expanded) 90f else 0f),
                    tint = appleGray().copy(alpha = 0.5f)
                )
            },
            onClick = onToggle
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 52.dp),
                content = content
            )
        }
    }
}

// Helper extension for rotation without importing graphics package
@Composable
private fun Modifier.graphicsRotation(degrees: Float) = this  // placeholder
