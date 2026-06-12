package com.chartanalyzer.app.ui

import android.annotation.SuppressLint
import android.webkit.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chartanalyzer.app.models.*
import com.chartanalyzer.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

// ─── Root TradingView Chart screen ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvChartScreen(
    initialSymbol: String = "BTCUSDT",
    onBack: (() -> Unit)? = null
) {
    val viewModel: TvChartViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    var showSymbolSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // ── Bug fix: load initialSymbol whenever it changes (covers back→new selection)
    LaunchedEffect(initialSymbol) {
        if (state.symbol != initialSymbol || (!state.isChartReady && !state.isLoading)) {
            viewModel.loadSymbol(initialSymbol)
        }
    }

    if (showSymbolSearch) {
        SymbolSearchDialog(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSelect = { sym ->
                viewModel.loadSymbol(sym)
                showSymbolSearch = false
                searchQuery = ""
            },
            onDismiss = { showSymbolSearch = false; searchQuery = "" }
        )
    }

    if (state.showSettings) {
        ChartSettingsSheet(
            config = state.chartConfig,
            onUpdate = viewModel::updateConfig,
            onDismiss = viewModel::toggleSettings
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Symbol sub-bar (global header is shown above; this shows chart-specific controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appleSecondaryGroupedBackground())
                .padding(horizontal = AppleSpacing.sm, vertical = AppleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(18.dp),
                        tint = appleBlue())
                }
            }
            // Symbol + interval
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.symbol.removeSuffix("USDT"),
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = appleLabel())
                    Text("/USDT", style = MaterialTheme.typography.labelSmall,
                        color = appleSecondaryLabel())
                    ConnectionStatusBadge(state.streamStatus)
                }
                Text(state.interval.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = appleSecondaryLabel())
            }
            // Chart action buttons
            if (state.mlEnabled && state.mlPrediction != null) {
                MlSignalBadge(state.mlPrediction!!)
            } else {
                state.lastSignal?.let { sig -> SignalBadge(sig) }
            }
            IconButton(onClick = viewModel::toggleMlOverlay,
                modifier = Modifier.size(36.dp)) {
                Icon(if (state.mlEnabled) Icons.Filled.Psychology else Icons.Outlined.Psychology,
                    "ML", tint = if (state.mlEnabled) Color(0xFFBC8CFF) else appleSecondaryLabel(),
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = viewModel::toggleLunarOverlay,
                modifier = Modifier.size(36.dp)) {
                Text(if (state.lunarOverlayEnabled) state.lunarPhase.emoji else "🌙",
                    fontSize = 16.sp)
            }
            IconButton(onClick = { showSymbolSearch = true },
                modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Search, "Search", tint = appleBlue(),
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = viewModel::toggleSettings,
                modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Tune, "Settings", tint = appleBlue(),
                    modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))

        // ── Multi-frame signal bar ─────────────────────────────────────────────
        AnimatedVisibility(visible = state.multiFrameActive) {
            MultiFrameBar(viewModel = viewModel, state = state)
        }

        // ── Main chart area ───────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            TvChartWebView(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Loading overlay — use Box-scoped AnimatedVisibility
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(
                            "Loading ${state.symbol} ${state.interval.displayName}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error overlay
            state.error?.let { err ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp))
                        Text(err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::clearError,
                            modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        // ── Lunar phase strip ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.lunarOverlayEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LunarPhaseStrip(state)
        }

        // ── Prediction info panel ──────────────────────────────────────────────
        if (state.mlEnabled && state.mlPrediction != null) {
            MlPredictionInfoPanel(state.mlPrediction!!)
        } else {
            state.lastSignal?.let { sig -> PredictionInfoPanel(sig) }
        }
    }
}

// ─── WebView with TradingView Lightweight Charts ───────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TvChartWebView(viewModel: TvChartViewModel, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)
                    displayZoomControls = false
                    builtInZoomControls = false
                    cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                }
                setBackgroundColor(0xFF0D1117.toInt())

                // JS bridge — called from chart.html
                addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun onChartReady() { viewModel.onChartReady() }

                    @JavascriptInterface
                    fun onTimeframeChange(tf: String) { viewModel.onTimeframeChange(tf) }
                }, "ChartBridge")

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // attachWebView stores the ref AND drains the JS queue
                        viewModel.attachWebView(this@apply)
                    }
                }

                loadUrl("file:///android_asset/chart.html")
            }
        },
        update = { /* queue-based, no action needed in update */ },
        modifier = modifier,
        onRelease = { viewModel.detachWebView() }
    )
}

// ─── Signal badge (top bar) ───────────────────────────────────────────────────

@Composable
fun SignalBadge(signal: PredictionSignal) {
    val (bg, text) = when (signal.type) {
        SignalType.STRONG_BUY  -> Color(0xFF00E676) to Color.Black
        SignalType.BUY         -> Color(0xFF26A69A) to Color.White
        SignalType.SELL        -> Color(0xFFFF6D00) to Color.White
        SignalType.STRONG_SELL -> Color(0xFFFF1744) to Color.White
        SignalType.NEUTRAL     -> Color(0xFF4A5568) to Color.White
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            signal.type.name.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = text,
            letterSpacing = 0.3.sp
        )
    }
}

// ─── ML signal badge (shows ensemble result) ──────────────────────────────────

@Composable
fun MlSignalBadge(pred: EnsemblePrediction) {
    val (bg, fg) = when (pred.signal) {
        SignalType.STRONG_BUY  -> Color(0xFF00B0FF) to Color.Black
        SignalType.BUY         -> Color(0xFF69F0AE) to Color.Black
        SignalType.SELL        -> Color(0xFFFF6D00) to Color.White
        SignalType.STRONG_SELL -> Color(0xFFFF1744) to Color.White
        SignalType.NEUTRAL     -> Color(0xFF4A5568) to Color.White
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = BorderStroke(1.dp, Color(0xFFBC8CFF)),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "ML " + pred.signal.name.replace('_', ' '),
                fontSize = 9.sp, fontWeight = FontWeight.Bold, color = fg,
                letterSpacing = 0.3.sp
            )
            Text(
                "${"%.0f".format(pred.confidence * 100)}% conf • " +
                "${"%.0f".format(pred.agreement * 100)}% agree",
                fontSize = 8.sp, color = fg.copy(alpha = 0.8f)
            )
        }
    }
}

// ─── Lunar Phase Strip (shown below chart when overlay enabled) ───────────────
// HIG: compact horizontal strip using Apple semantic colors + phase emoji

@Composable
fun LunarPhaseStrip(state: TvChartUiState) {
    // Determine lunar color: gold near Full Moon, green near New Moon, silver otherwise
    val lunarColor = when {
        state.lunarIllumination > 0.90 -> Color(0xFFFFD60A)   // Full Moon — gold
        state.lunarIllumination < 0.10 -> Color(0xFF30D158)   // New Moon  — systemGreen
        state.lunarScore > 0.4         -> Color(0xFF5AC8FA)   // waxing strong — systemTeal
        state.lunarScore < -0.4        -> Color(0xFFFF9F0A)   // waning strong — systemOrange
        else                            -> Color(0xFFAEAEB2)   // neutral — systemGray2
    }

    val biasLabel = when {
        state.lunarIllumination < 0.05 -> "🟢 Buy Zone — New Moon"
        state.lunarIllumination > 0.95 -> "⚠️ Reversal Watch — Full Moon"
        LunarCalculator.isWaxing(state.lunarAge) &&
            state.lunarScore > 0.3     -> "↑ Waxing — Bullish Bias"
        !LunarCalculator.isWaxing(state.lunarAge) &&
            state.lunarScore < -0.3    -> "↓ Waning — Bearish Bias"
        else                            -> "${state.lunarPhase.label}"
    }

    Surface(
        color = lunarColor.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, lunarColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Phase emoji + label
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(state.lunarPhase.emoji, fontSize = 18.sp)
                Column {
                    Text(biasLabel,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = lunarColor)
                    Text(
                        "Age: ${"%.1f".format(state.lunarAge)} d  •  " +
                        "Illum: ${"%.0f".format(state.lunarIllumination * 100)}%  •  " +
                        "Score: ${"%.2f".format(state.lunarScore)}",
                        fontSize = 10.sp, color = appleSecondaryLabel())
                }
            }

            // Countdown to next event
            Column(horizontalAlignment = Alignment.End) {
                val toNew  = state.daysToNextNewMoon
                val toFull = state.daysToNextFullMoon
                val nextIsNew = toNew < toFull
                Text(
                    (if (nextIsNew) "🌑" else "🌕") + " " +
                    "${"%.1f".format(if (nextIsNew) toNew else toFull)} d",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = if (nextIsNew) Color(0xFF30D158) else Color(0xFFFFD60A)
                )
                Text("Next ${if (nextIsNew) "New" else "Full"} Moon",
                    fontSize = 9.sp, color = appleSecondaryLabel())
            }
        }
    }
}

// ─── Multi-timeframe signal bar ───────────────────────────────────────────────

@Composable
fun MultiFrameBar(viewModel: TvChartViewModel, state: TvChartUiState) {
    val signals = viewModel.getMultiFrameSignals()
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("MTF:", style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
        signals.forEach { (interval, sig) ->
            MultiFrameChip(interval = interval, signal = sig,
                isActive = state.interval == interval,
                onClick = { viewModel.changeInterval(interval) })
        }
    }
    Divider(thickness = 0.5.dp)
}

@Composable
fun MultiFrameChip(
    interval: CandleInterval,
    signal: PredictionSignal?,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val (bgColor, fgColor) = if (signal != null) when (signal.type) {
        SignalType.STRONG_BUY  -> Color(0xFF00E676) to Color.Black
        SignalType.BUY         -> Color(0xFF26A69A) to Color.White
        SignalType.SELL        -> Color(0xFFFF6D00) to Color.White
        SignalType.STRONG_SELL -> Color(0xFFFF1744) to Color.White
        SignalType.NEUTRAL     -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    } else MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(interval.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fgColor)
            signal?.let {
                Text(
                    "${"%.0f".format(it.confidence * 100)}%",
                    fontSize = 9.sp, color = fgColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ─── Prediction info panel (bottom) ──────────────────────────────────────────

@Composable
fun PredictionInfoPanel(signal: PredictionSignal) {
    val cs = MaterialTheme.colorScheme

    Surface(
        color = cs.surface,
        border = BorderStroke(0.5.dp, cs.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Prediction Engine",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    signal.source.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.primary
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Buy strength
                StrengthMeter(
                    label = "Buy Strength",
                    value = signal.buyStrength,
                    color = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
                // Sell strength
                StrengthMeter(
                    label = "Sell Strength",
                    value = signal.sellStrength,
                    color = Color(0xFFFF1744),
                    modifier = Modifier.weight(1f)
                )
                // Confidence
                StrengthMeter(
                    label = "Confidence",
                    value = signal.confidence * 100.0,
                    color = Color(0xFFBC8CFF),
                    modifier = Modifier.weight(1f)
                )
            }

            if (signal.targetPrice != null || signal.stopLoss != null || signal.riskReward != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    signal.targetPrice?.let {
                        PredMetric("Target", fmtPrice(it), Color(0xFF3FB950), Modifier.weight(1f))
                    }
                    signal.stopLoss?.let {
                        PredMetric("Stop Loss", fmtPrice(it), Color(0xFFF85149), Modifier.weight(1f))
                    }
                    signal.riskReward?.let {
                        PredMetric("R:R", "${"%.2f".format(it)}x", Color(0xFFE3B341), Modifier.weight(1f))
                    }
                }
            }

            if (signal.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    signal.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 2
                )
            }
        }
    }
}

// ─── ML Prediction info panel (bottom bar when ML enabled) ───────────────────

@Composable
fun MlPredictionInfoPanel(pred: EnsemblePrediction) {
    val cs = MaterialTheme.colorScheme
    val (sigColor, sigIcon) = when (pred.signal) {
        SignalType.STRONG_BUY  -> Color(0xFF00B0FF) to "▲▲"
        SignalType.BUY         -> Color(0xFF69F0AE) to "▲"
        SignalType.SELL        -> Color(0xFFFF6D00) to "▼"
        SignalType.STRONG_SELL -> Color(0xFFFF1744) to "▼▼"
        SignalType.NEUTRAL     -> cs.onSurfaceVariant to "–"
    }

    Surface(
        color = cs.surface,
        border = BorderStroke(0.5.dp, Color(0xFFBC8CFF).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Psychology, null,
                        tint = Color(0xFFBC8CFF), modifier = Modifier.size(16.dp))
                    Text("ML Ensemble", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFBC8CFF).copy(alpha = 0.15f)) {
                        Text("GB + LSTM",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            fontSize = 9.sp, color = Color(0xFFBC8CFF))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(sigIcon, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = sigColor)
                    Text(pred.signal.name.replace('_', ' '),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = sigColor)
                }
            }
            Spacer(Modifier.height(6.dp))

            // GB | LSTM | Ensemble buy probabilities
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MlModelProb("GB Buy",
                    pred.gbPrediction.buyProbability * 100, Color(0xFF00B0FF), Modifier.weight(1f))
                MlModelProb("LSTM Buy",
                    pred.lstmPrediction.buyProbability * 100, Color(0xFF69F0AE), Modifier.weight(1f))
                MlModelProb("Ens. Buy",
                    pred.buyProbability, Color(0xFF00E676), Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            // GB | LSTM | Ensemble sell probabilities
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MlModelProb("GB Sell",
                    pred.gbPrediction.sellProbability * 100, Color(0xFFFF6D00), Modifier.weight(1f))
                MlModelProb("LSTM Sell",
                    pred.lstmPrediction.sellProbability * 100, Color(0xFFEF9A9A), Modifier.weight(1f))
                MlModelProb("Ens. Sell",
                    pred.sellProbability, Color(0xFFFF1744), Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))

            // Agreement + Confidence
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StrengthMeter("Confidence", pred.confidence * 100, Color(0xFFBC8CFF), Modifier.weight(1f))
                StrengthMeter("Agreement",  pred.agreement  * 100, Color(0xFF388BFD), Modifier.weight(1f))
            }

            if (pred.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(pred.notes, fontSize = 9.sp, color = cs.onSurfaceVariant, maxLines = 2)
            }

            // Top features
            if (pred.topFeatures.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text("Top Features", fontSize = 9.sp,
                    color = cs.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pred.topFeatures.take(3).forEach { (name, score) ->
                        Surface(shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF388BFD).copy(alpha = 0.1f),
                            modifier = Modifier.wrapContentWidth()) {
                            Text(
                                name.replace('_', ' ').take(14),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                fontSize = 8.sp, color = Color(0xFF388BFD)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MlModelProb(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(modifier = Modifier
                .fillMaxWidth(fraction = (value / 100.0).coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(color))
        }
        Spacer(Modifier.height(1.dp))
        Text("${"%.0f".format(value)}%",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun StrengthMeter(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (value / 100.0).coerceIn(0.0, 1.0).toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("${"%.0f".format(value)}",
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun PredMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ─── Settings sheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartSettingsSheet(
    config: ChartConfig,
    onUpdate: (ChartConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var cfg by remember { mutableStateOf(config) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chart Settings") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Overlay Layers", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { SettingsToggle("Volume Histogram",        cfg.showVolume)     { cfg = cfg.copy(showVolume   = it) } }
                item { SettingsToggle("Buy Strength Series",     cfg.showBuyStrength){ cfg = cfg.copy(showBuyStrength  = it) } }
                item { SettingsToggle("Sell Strength Series",    cfg.showSellStrength){ cfg = cfg.copy(showSellStrength = it) } }
                item { SettingsToggle("Confidence Score Series", cfg.showConfidence) { cfg = cfg.copy(showConfidence  = it) } }
                item { SettingsToggle("Signal Markers",          cfg.showSignalMarkers){ cfg = cfg.copy(showSignalMarkers = it) } }
                item { SettingsToggle("Support/Resistance Zones",cfg.showPriceZones) { cfg = cfg.copy(showPriceZones  = it) } }
                item { SettingsToggle("Crosshair",               cfg.showCrosshair)  { cfg = cfg.copy(showCrosshair  = it) } }
            }
        },
        confirmButton = {
            Button(onClick = { onUpdate(cfg); onDismiss() }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onToggle,
            modifier = Modifier.scale(0.8f))
    }
}

// ─── Symbol search dialog ─────────────────────────────────────────────────────

@Composable
fun SymbolSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val suggestions = remember(query) {
        if (query.isBlank()) PopularSymbols.allSymbols
        else PopularSymbols.allSymbols.filter {
            it.contains(query.uppercase())
        } + (if (query.length >= 2 && !query.uppercase().endsWith("USDT"))
            listOf("${query.uppercase()}USDT") else emptyList())
    }.distinct().take(15)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, null,
                    tint = MaterialTheme.colorScheme.primary)
                Text("Select Symbol")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("e.g. BTC, ETH, SOL...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(suggestions) { sym ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(sym) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sym.removeSuffix("USDT"),
                                fontWeight = FontWeight.Medium)
                            Text("/USDT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Divider(thickness = 0.3.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun fmtPrice(p: Double): String = when {
    p >= 1000 -> NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2; minimumFractionDigits = 2 }.format(p)
    p >= 1    -> "%.4f".format(p)
    p >= 0.01 -> "%.6f".format(p)
    else      -> "%.8f".format(p)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Modifier.scale(s: Float) = this  // placeholder - Switch doesn't need scaling in M3
