package com.chartanalyzer.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chartanalyzer.app.utils.*
import com.chartanalyzer.app.ui.theme.*
import com.chartanalyzer.app.models.*
import java.text.SimpleDateFormat
import java.util.*

// ─── Root Portfolio & Alerts Screen ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen() {
    val viewModel: MLViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Positions", "Alerts", "Summary")

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-header with tab-specific actions
        Row(modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountBalance, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text("Portfolio", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall)
                Text("• Paper trading",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                if (selectedTab == 1 && state.alertHistory.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearAlerts,
                        modifier = Modifier.size(AppleSpacing.xxxl)) {
                        Icon(Icons.Outlined.ClearAll, "Clear alerts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (selectedTab == 0 && state.portfolioSummary != null) {
                    IconButton(onClick = viewModel::clearPortfolio,
                        modifier = Modifier.size(AppleSpacing.xxxl)) {
                        Icon(Icons.Outlined.DeleteSweep, "Clear positions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(title, fontSize = 13.sp)
                            // Badge for alerts
                            if (i == 1 && state.alertHistory.isNotEmpty()) {
                                Surface(shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            minOf(state.alertHistory.size, 99).toString(),
                                            fontSize = 9.sp, color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> PositionsTab(state, viewModel)
            1 -> AlertsTab(state, viewModel)
            2 -> SummaryTab(state)
        }
    }
}

// ─── Positions Tab ────────────────────────────────────────────────────────────

@Composable
fun PositionsTab(state: MLUiState, viewModel: MLViewModel) {
    val open   = state.openPositions
    val closed = state.closedPositions

    if (open.isEmpty() && closed.isEmpty()) {
        EmptyPortfolioPlaceholder()
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (open.isNotEmpty()) {
            item {
                Text("Open Positions (${open.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }
            items(open, key = { it.id }) { pos ->
                PositionCard(pos,
                    onClose = { viewModel.closePosition(pos.id, pos.entryPrice * 1.001) },
                    onDelete = { viewModel.deletePosition(pos.id) }
                )
            }
        }

        if (closed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Closed Positions (${closed.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(closed.take(20), key = { it.id }) { pos ->
                PositionCard(pos, onClose = {}, onDelete = { viewModel.deletePosition(pos.id) })
            }
        }
    }
}

@Composable
fun PositionCard(pos: Position, onClose: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fmt = SimpleDateFormat("MMM d HH:mm", Locale.getDefault())
    val isBuy = pos.side == PositionSide.LONG
    val signalColor = if (isBuy) Color(0xFF3FB950) else Color(0xFFF85149)
    val pnl = pos.pnlPct
    val pnlColor = when {
        pnl == null -> cs.onSurfaceVariant
        pnl > 0     -> Color(0xFF3FB950)
        else        -> Color(0xFFF85149)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            0.5.dp,
            when (pos.status) {
                PositionStatus.OPEN       -> signalColor.copy(alpha = 0.4f)
                PositionStatus.TARGET_HIT -> Color(0xFF3FB950).copy(alpha = 0.4f)
                PositionStatus.STOPPED_OUT -> Color(0xFFF85149).copy(alpha = 0.4f)
                else -> cs.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Header row
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp)
                        .background(if (pos.isOpen) signalColor else cs.onSurfaceVariant.copy(alpha = 0.3f),
                            CircleShape))
                    Text(pos.symbol, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = signalColor.copy(alpha = 0.15f)) {
                        Text(
                            "${if (isBuy) "LONG" else "SHORT"} • ${pos.entrySignal.name.replace('_',' ')}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp, color = signalColor, fontWeight = FontWeight.Bold
                        )
                    }
                }
                // PnL badge
                if (pnl != null) {
                    Surface(shape = RoundedCornerShape(6.dp), color = pnlColor.copy(alpha = 0.15f)) {
                        Text(
                            "${if (pnl > 0) "+" else ""}${"%.2f".format(pnl)}%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pnlColor
                        )
                    }
                }
            }

            // Price info
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PriceLabel("Entry", "${"%.4f".format(pos.entryPrice)}", cs.onSurface)
                pos.targetPrice?.let { PriceLabel("Target", "${"%.4f".format(it)}", Color(0xFF3FB950)) }
                pos.stopLoss?.let {   PriceLabel("Stop",   "${"%.4f".format(it)}", Color(0xFFF85149)) }
                pos.exitPrice?.let {  PriceLabel("Exit",   "${"%.4f".format(it)}", pnlColor) }
            }

            // Meta row
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${pos.entrySource} • Conf: ${"%.0f".format(pos.entryConfidence * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                Text(fmt.format(Date(pos.entryTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant)
            }

            // Status & actions
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(pos.status)
                if (pos.isOpen) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) { Text("Close", fontSize = 11.sp) }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.DeleteOutline, "Delete",
                                modifier = Modifier.size(16.dp),
                                tint = cs.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PriceLabel(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
fun StatusBadge(status: PositionStatus) {
    val (bg, fg, text) = when (status) {
        PositionStatus.OPEN        -> Triple(Color(0xFF388BFD).copy(alpha = 0.15f), Color(0xFF388BFD), "OPEN")
        PositionStatus.CLOSED      -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "CLOSED")
        PositionStatus.TARGET_HIT  -> Triple(Color(0xFF3FB950).copy(alpha = 0.15f), Color(0xFF3FB950), "🎯 TARGET HIT")
        PositionStatus.STOPPED_OUT -> Triple(Color(0xFFF85149).copy(alpha = 0.15f), Color(0xFFF85149), "⛔ STOPPED OUT")
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
fun EmptyPortfolioPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.AccountBalance, null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            Text("No positions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Positions open when ML or TA signals fire\nand are accepted from the chart screen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center)
        }
    }
}

// ─── Alerts Tab ───────────────────────────────────────────────────────────────

@Composable
fun AlertsTab(state: MLUiState, viewModel: MLViewModel) {
    if (state.alertHistory.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.NotificationsNone, null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Text("No alerts yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Strong signals will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        return
    }

    // Alert config card + list
    LazyColumn(contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {

        item { AlertConfigCard(state.alertConfig, viewModel::updateAlertConfig) }

        item {
            Text("${state.alertHistory.size} recent alerts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(state.alertHistory, key = { it.id }) { alert ->
            AlertCard(alert)
        }
    }
}

@Composable
fun AlertConfigCard(config: AlertConfig, onUpdate: (AlertConfig) -> Unit) {
    var cfg by remember { mutableStateOf(config) }
    var expanded by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, null,
                        tint = if (cfg.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                    Text("Alert Settings", fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = cfg.enabled,
                        onCheckedChange = { cfg = cfg.copy(enabled = it); onUpdate(cfg) })
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null, modifier = Modifier.size(20.dp))
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Divider()
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Push notifications", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = cfg.pushNotificationsEnabled,
                            onCheckedChange = { cfg = cfg.copy(pushNotificationsEnabled = it); onUpdate(cfg) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("TA signals", style = MaterialTheme.typography.bodySmall)
                            Text("Alert on rule-based signals",
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = cfg.alertOnTASignals,
                            onCheckedChange = { cfg = cfg.copy(alertOnTASignals = it); onUpdate(cfg) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Moderate signals", style = MaterialTheme.typography.bodySmall)
                            Text("Also alert BUY/SELL (not just STRONG)",
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = cfg.alertOnModerateSigs,
                            onCheckedChange = { cfg = cfg.copy(alertOnModerateSigs = it); onUpdate(cfg) })
                    }
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Min Confidence", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.0f".format(cfg.minConfidence * 100)}%",
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                        Slider(value = cfg.minConfidence.toFloat(),
                            onValueChange = { cfg = cfg.copy(minConfidence = it.toDouble()); onUpdate(cfg) },
                            valueRange = 0.4f..0.95f, modifier = Modifier.height(32.dp))
                    }
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cooldown (min)", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${cfg.cooldownMinutes}m",
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                        Slider(value = cfg.cooldownMinutes.toFloat(),
                            onValueChange = { cfg = cfg.copy(cooldownMinutes = it.toInt()); onUpdate(cfg) },
                            valueRange = 1f..60f, steps = 58, modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AlertCard(alert: SignalAlert) {
    val cs = MaterialTheme.colorScheme
    val fmt = SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault())
    val (color, icon) = when (alert.signalType) {
        SignalType.STRONG_BUY  -> Color(0xFF00E676) to "🚀"
        SignalType.BUY         -> Color(0xFF26A69A) to "📈"
        SignalType.SELL        -> Color(0xFFFF6D00) to "📉"
        SignalType.STRONG_SELL -> Color(0xFFFF1744) to "🔻"
        SignalType.NEUTRAL     -> cs.onSurfaceVariant to "➖"
    }

    Card(shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f))) {
        Row(modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top) {
            Text(icon, fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(alert.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = color, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(fmt.format(Date(alert.timestamp)),
                        fontSize = 10.sp, color = cs.onSurfaceVariant)
                }
                Text(alert.body, style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface, lineHeight = 16.sp)
                Surface(shape = RoundedCornerShape(4.dp),
                    color = if (alert.source == AlertSource.ML_ENSEMBLE)
                        Color(0xFFBC8CFF).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)) {
                    Text(
                        alert.source.name.replace('_', ' '),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        color = if (alert.source == AlertSource.ML_ENSEMBLE)
                            Color(0xFFBC8CFF) else cs.primary
                    )
                }
            }
        }
    }
}

// ─── Summary Tab ──────────────────────────────────────────────────────────────

@Composable
fun SummaryTab(state: MLUiState) {
    val summary = state.portfolioSummary
    if (summary == null || summary.totalPositions == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No closed positions to summarize",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {

        item {
            PortfolioSummaryCard(summary)
        }

        summary.bestTrade?.let {
            item {
                Text("Best Trade", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                PositionCard(it, onClose = {}, onDelete = {})
            }
        }

        summary.worstTrade?.let {
            item {
                Text("Worst Trade", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                PositionCard(it, onClose = {}, onDelete = {})
            }
        }
    }
}

@Composable
fun PortfolioSummaryCard(summary: PortfolioSummary) {
    val cs = MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Portfolio Overview", fontWeight = FontWeight.SemiBold)
            Divider()

            // Main metrics
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell("Total P&L",
                    "${if (summary.totalPnlPct > 0) "+" else ""}${"%.2f".format(summary.totalPnlPct)}%",
                    if (summary.totalPnlPct > 0) Color(0xFF3FB950) else cs.error,
                    "Paper", Modifier.weight(1f))
                MetricCell("Win Rate",
                    "${"%.1f".format(summary.winRate * 100)}%",
                    if (summary.winRate > 0.5) Color(0xFF3FB950) else cs.error,
                    "${summary.winningPositions}W ${summary.losingPositions}L",
                    Modifier.weight(1f))
                MetricCell("Profit Factor",
                    "${"%.2f".format(summary.profitFactor)}x",
                    if (summary.profitFactor > 1.5) Color(0xFF3FB950)
                    else if (summary.profitFactor > 1.0) Color(0xFFE3B341) else cs.error,
                    "Gross W/L",
                    Modifier.weight(1f))
            }

            Divider()

            // Secondary metrics
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell("Avg Win",
                    "+${"%.2f".format(summary.avgWinPct)}%",
                    Color(0xFF3FB950), "Per trade", Modifier.weight(1f))
                MetricCell("Avg Loss",
                    "-${"%.2f".format(summary.avgLossPct)}%",
                    cs.error, "Per trade", Modifier.weight(1f))
                MetricCell("Open",
                    "${summary.openPositions}",
                    Color(0xFF388BFD), "Active", Modifier.weight(1f))
            }

            Text("⚠️ All positions are paper trades (simulated). No real money involved.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}
