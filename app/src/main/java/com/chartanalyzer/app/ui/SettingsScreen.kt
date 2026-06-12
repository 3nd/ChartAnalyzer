package com.chartanalyzer.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chartanalyzer.app.models.*
import com.chartanalyzer.app.ui.theme.*
import com.chartanalyzer.app.utils.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    @Suppress("UNUSED_PARAMETER")
    currentProvider: MarketDataProvider,
    onProviderChange: (MarketDataProvider) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit
) {
    val context         = LocalContext.current
    val clipboard       = LocalClipboardManager.current
    val logEntries      by AppLog.entries.collectAsState()

    var networkLog  by remember { mutableStateOf(AppLog.networkLogEnabled) }
    var stateLog    by remember { mutableStateOf(AppLog.stateLogEnabled) }
    var logExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = AppleSpacing.md, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(AppleSpacing.xl)
    ) {
        // ── Section 1: Version Information ────────────────────────────────────
        item {
            HigSectionHeader("About ChartAnalyzer")
            Spacer(Modifier.height(AppleSpacing.xs))
            HigGroupedCard {
                // App icon + name + version
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppleSpacing.base),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                        .background(appleBlue()),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, null,
                            tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column {
                        Text("ChartAnalyzer", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = appleLabel())
                        Text("v2.0 — Major Release", style = MaterialTheme.typography.titleMedium,
                            color = appleBlue())
                        Text("Advanced AI-powered crypto chart analysis",
                            style = MaterialTheme.typography.labelSmall, color = appleSecondaryLabel())
                    }
                }
                HigDivider()
                HigInfoRow("Version",         "2.0.0 (Build 13)")
                HigDivider()
                HigInfoRow("Release Type",    "Major")
                HigDivider()
                HigInfoRow("ML Features",     "56 (45 TA + 6 Lunar + 5 Shemitah)")
                HigDivider()
                HigInfoRow("Frameworks",      "29 Technical Analysis Frameworks")
                HigDivider()
                HigInfoRow("AI Providers",    "7 supported (3 free tier)")
                HigDivider()
                HigInfoRow("Data Providers",  "6 market data sources")
                HigDivider()
                HigInfoRow("Min Android",     "API 26 (Android 8.0)")
                HigDivider()
                HigInfoRow("Target Android",  "API 35 (Android 15+)")
            }
        }

        // ── Section 2: Market Data Provider ───────────────────────────────────
        item {
            HigSectionHeader("Market Data Provider",
                footnote = "All providers shown use free public WebSocket/REST — no API key required")
            Spacer(Modifier.height(AppleSpacing.xs))
            HigGroupedCard {
                MarketDataProvider.values().forEachIndexed { i, provider ->
                    DataProviderRow(
                        provider = provider,
                        isSelected = provider == currentProvider,
                        onSelect = { onProviderChange(provider) }
                    )
                    if (i < MarketDataProvider.values().size - 1) HigDivider()
                }
            }
        }

        // ── Section 3: Changelog ──────────────────────────────────────────────
        item {
            HigSectionHeader("Changelog")
            Spacer(Modifier.height(AppleSpacing.xs))
            Column(verticalArrangement = Arrangement.spacedBy(AppleSpacing.md)) {
                AppChangelog.entries.forEach { entry ->
                    ChangelogCard(entry)
                }
            }
        }

        // ── Section 4: Application Log ────────────────────────────────────────
        item {
            HigSectionHeader("Application Log",
                footnote = "Diagnostic logging — disabled by default")
            Spacer(Modifier.height(AppleSpacing.xs))
            HigGroupedCard {
                // Collapse/expand toggle header
                Surface(onClick = { logExpanded = !logExpanded },
                    color = appleSecondaryGroupedBackground()) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BugReport, null,
                                tint = appleOrange(), modifier = Modifier.size(18.dp))
                            Column {
                                Text("Diagnostic Log",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold, color = appleLabel())
                                Text("${logEntries.size} entries",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appleSecondaryLabel())
                            }
                        }
                        Icon(if (logExpanded) Icons.Filled.KeyboardArrowUp
                             else Icons.Filled.KeyboardArrowDown,
                            null, tint = appleGray().copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp))
                    }
                }

                AnimatedVisibility(visible = logExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit  = shrinkVertically() + fadeOut()) {
                    Column {
                        HigDivider()

                        // Checkboxes
                        Column(modifier = Modifier.padding(
                            horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AppleSpacing.sm)) {

                            // Network Trace checkbox
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                                verticalAlignment = Alignment.Top) {
                                Checkbox(
                                    checked = networkLog,
                                    onCheckedChange = {
                                        networkLog = it
                                        AppLog.networkLogEnabled = it
                                        if (it) AppLog.info("Settings","Network trace enabled")
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = appleBlue(),
                                        uncheckedColor = appleGray()
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Network Trace / HTTP Log",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Medium, color = appleLabel())
                                    Text("Records all HTTP request/response cycles, " +
                                         "JSON format validation, and API errors. " +
                                         "Verifies that data sent matches expected format " +
                                         "and server responses are well-formed.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = appleSecondaryLabel(), lineHeight = 16.sp)
                                }
                            }

                            // State Log checkbox
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                                verticalAlignment = Alignment.Top) {
                                Checkbox(
                                    checked = stateLog,
                                    onCheckedChange = {
                                        stateLog = it
                                        AppLog.stateLogEnabled = it
                                        if (it) AppLog.info("Settings","State log enabled")
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = appleBlue(),
                                        uncheckedColor = appleGray()
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("State Log",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Medium, color = appleLabel())
                                    Text("Tracks how incoming data updates the app's " +
                                         "internal state — candle updates, prediction " +
                                         "changes, signal transitions, and UI state mutations.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = appleSecondaryLabel(), lineHeight = 16.sp)
                                }
                            }
                        }

                        HigDivider()

                        // Log action buttons
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm)) {
                            // Save to file
                            OutlinedButton(
                                onClick = { saveLogToFile(context, AppLog.toText()) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(AppleShapes.sm),
                                border = BorderStroke(1.dp, appleSeparator()),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.SaveAlt, null,
                                    modifier = Modifier.size(14.dp), tint = appleBlue())
                                Spacer(Modifier.width(4.dp))
                                Text("Save", style = MaterialTheme.typography.labelSmall,
                                    color = appleBlue())
                            }
                            // Copy to clipboard
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(AppLog.toText()))
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(AppleShapes.sm),
                                border = BorderStroke(1.dp, appleSeparator()),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null,
                                    modifier = Modifier.size(14.dp), tint = appleBlue())
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall,
                                    color = appleBlue())
                            }
                            // Clear
                            OutlinedButton(
                                onClick = { AppLog.clear() },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(AppleShapes.sm),
                                border = BorderStroke(1.dp, appleRed().copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.DeleteForever, null,
                                    modifier = Modifier.size(14.dp), tint = appleRed())
                                Spacer(Modifier.width(4.dp))
                                Text("Clear", style = MaterialTheme.typography.labelSmall,
                                    color = appleRed())
                            }
                        }

                        // Log output
                        if (logEntries.isNotEmpty()) {
                            HigDivider()
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .background(Color(0xFF0D1117))
                                .padding(AppleSpacing.sm)
                                .verticalScroll(rememberScrollState())) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                                    logEntries.take(100).forEach { entry ->
                                        val color = when (entry.level) {
                                            com.chartanalyzer.app.models.LogLevel.NETWORK ->
                                                Color(0xFF64D2FF)
                                            com.chartanalyzer.app.models.LogLevel.STATE   ->
                                                Color(0xFF30D158)
                                            com.chartanalyzer.app.models.LogLevel.ERROR   ->
                                                Color(0xFFFF453A)
                                            com.chartanalyzer.app.models.LogLevel.INFO    ->
                                                Color(0xFFFFD60A)
                                        }
                                        Text(
                                            "[${fmt.format(Date(entry.timestamp))}]" +
                                            "[${entry.level.name}] ${entry.tag}: ${entry.message}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = color,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        } else if (networkLog || stateLog) {
                            Box(modifier = Modifier.fillMaxWidth()
                                .padding(AppleSpacing.base), contentAlignment = Alignment.Center) {
                                Text("No log entries yet — interact with the app to generate logs",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = appleSecondaryLabel())
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(AppleSpacing.xl)) }
    }
}

// ─── Data Provider Row ────────────────────────────────────────────────────────

@Composable
fun DataProviderRow(
    provider: MarketDataProvider,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    var expanded by remember { mutableStateOf(isSelected) }

    Column(modifier = Modifier.clickable { expanded = !expanded; if (!expanded) onSelect() }) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppleSpacing.xxxl)
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md)) {

            val accentColor = try {
                Color(android.graphics.Color.parseColor(provider.accentHex))
            } catch (_: Exception) { appleBlue() }

            // Provider emoji in accent box
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(AppleShapes.sm))
                .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Text(provider.emoji, fontSize = 16.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(provider.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold, color = appleLabel())
                    if (provider.isDefault) {
                        Surface(shape = RoundedCornerShape(4.dp),
                            color = appleBlue().copy(alpha = 0.12f)) {
                            Text("Default",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                fontSize = 9.sp, color = appleBlue())
                        }
                    }
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = appleGreen().copy(alpha = 0.12f)) {
                        Text("FREE", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                            color = appleGreen())
                    }
                }
                if (provider.wsBaseUrl.isNotEmpty()) {
                    Text("WebSocket + REST",
                        style = MaterialTheme.typography.labelSmall, color = appleSecondaryLabel())
                } else {
                    Text("REST only (free tier)",
                        style = MaterialTheme.typography.labelSmall, color = appleOrange())
                }
            }

            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, "Selected",
                    tint = appleGreen(), modifier = Modifier.size(20.dp))
            }
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ChevronRight,
                null, tint = appleGray().copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(
                start = AppleSpacing.base + 34.dp + AppleSpacing.md,
                end = AppleSpacing.base, bottom = AppleSpacing.md)) {
                HigDivider()
                Spacer(Modifier.height(AppleSpacing.sm))
                Text(provider.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = appleSecondaryLabel(), lineHeight = 19.sp)
                Spacer(Modifier.height(AppleSpacing.sm))
                if (!isSelected) {
                    Button(onClick = onSelect,
                        modifier = Modifier.fillMaxWidth().height(AppleSpacing.xxxl),
                        shape = RoundedCornerShape(AppleShapes.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appleBlue(), contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(0.dp)) {
                        Text("Use ${provider.displayName}", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Surface(modifier = Modifier.fillMaxWidth().height(AppleSpacing.xxxl),
                        shape = RoundedCornerShape(AppleShapes.sm),
                        color = appleGreen().copy(alpha = 0.1f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null,
                                    tint = appleGreen(), modifier = Modifier.size(16.dp))
                                Text("Currently Active", fontWeight = FontWeight.SemiBold,
                                    color = appleGreen())
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Changelog Card ───────────────────────────────────────────────────────────

@Composable
fun ChangelogCard(entry: ChangelogEntry) {
    val typeColor = when (entry.type) {
        "Major" -> appleRed()
        "Minor" -> appleBlue()
        else    -> appleGray()
    }
    HigGroupedCard {
        Column(modifier = Modifier.padding(AppleSpacing.base),
            verticalArrangement = Arrangement.spacedBy(AppleSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically) {
                Text("v${entry.version}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = appleLabel())
                Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.12f)) {
                    Text(entry.type,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = typeColor)
                }
                Spacer(Modifier.weight(1f))
                Text(entry.date, style = MaterialTheme.typography.labelSmall,
                    color = appleSecondaryLabel())
            }
            entry.changes.forEachIndexed { i, change ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top) {
                    Text("${i + 1}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = appleSecondaryLabel(),
                        modifier = Modifier.width(20.dp))
                    Text(change, style = MaterialTheme.typography.titleMedium,
                        color = appleLabel(), lineHeight = 19.sp,
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── Log file save helper ─────────────────────────────────────────────────────

private fun saveLogToFile(context: android.content.Context, content: String) {
    try {
        val fmt  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val name = "log_v2.0_${fmt.format(Date())}.txt"
        val file = File(context.getExternalFilesDir(null), name)
        file.writeText(content)
        AppLog.info("Settings", "Log saved to ${file.absolutePath}")
    } catch (e: Exception) {
        AppLog.error("Settings", "Failed to save log: ${e.message}")
    }
}
