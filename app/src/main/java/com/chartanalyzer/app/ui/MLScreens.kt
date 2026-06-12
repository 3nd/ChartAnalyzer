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
import com.chartanalyzer.app.models.*
import com.chartanalyzer.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ─── Root ML Prediction Screen ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MLPredictionScreen() {
    val viewModel: MLViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    val tabs = listOf("Train", "Predictions", "Features", "Backtest")

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-header: ML status (main header shown in global top bar)
        Row(modifier = Modifier.fillMaxWidth()
            .background(appleSecondaryGroupedBackground())
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Psychology, null,
                    tint = applePurple(), modifier = Modifier.size(16.dp))
                Text("ML Engine", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, color = appleLabel())
                Text("GB + LSTM", style = MaterialTheme.typography.labelSmall,
                    color = appleSecondaryLabel())
            }
            state.modelInfo?.let {
                Surface(shape = RoundedCornerShape(AppleShapes.full),
                    color = appleGreen().copy(alpha = 0.12f)) {
                    Text("Trained", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = appleGreen())
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))

        // Live prediction banner
        state.latestPrediction?.let { pred ->
            LivePredictionBanner(pred)
        }

        // Tab bar
        ScrollableTabRow(selectedTabIndex = state.selectedTab, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, tab ->
                Tab(selected = state.selectedTab == i,
                    onClick = { viewModel.setTab(i) },
                    text = { Text(tab, fontSize = 13.sp) })
            }
        }

        when (state.selectedTab) {
            0 -> TrainingTab(state, viewModel)
            1 -> PredictionsTab(state)
            2 -> FeaturesTab(state)
            3 -> BacktestTab(state, viewModel)
        }
    }
}

// ─── Live Prediction Banner — HIG: systemGreen / systemRed fills ─────────────

@Composable
fun LivePredictionBanner(pred: EnsemblePrediction) {
    val (bg, fg, icon) = when (pred.signal) {
        SignalType.STRONG_BUY  -> Triple(appleGreen(),  Color.White, "▲▲")
        SignalType.BUY         -> Triple(appleGreen().copy(alpha = 0.85f), Color.White, "▲")
        SignalType.SELL        -> Triple(appleRed().copy(alpha = 0.85f), Color.White, "▼")
        SignalType.STRONG_SELL -> Triple(appleRed(), Color.White, "▼▼")
        SignalType.NEUTRAL     -> Triple(appleGray().copy(alpha = 0.15f), appleSecondaryLabel(), "–")
    }
    Surface(color = bg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg)
                Column {
                    Text(pred.signal.name.replace('_', ' '),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp, color = fg)
                    Text("Conf: ${"%.0f".format(pred.confidence * 100)}%  •  " +
                         "Agree: ${"%.0f".format(pred.agreement * 100)}%",
                        fontSize = 12.sp, color = fg.copy(alpha = 0.85f))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md)) {
                    MiniProb("GB",   pred.gbPrediction.buyProbability   * 100,
                             pred.gbPrediction.sellProbability   * 100, fg)
                    MiniProb("LSTM", pred.lstmPrediction.buyProbability * 100,
                             pred.lstmPrediction.sellProbability * 100, fg)
                }
                Text("@ ${"%.2f".format(pred.price)}", fontSize = 11.sp,
                    color = fg.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
fun MiniProb(label: String, buy: Double, sell: Double, fg: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = fg.copy(alpha = 0.7f))
        Text("B${"%.0f".format(buy)}%", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = fg)
        Text("S${"%.0f".format(sell)}%", fontSize = 10.sp, color = fg.copy(alpha = 0.7f))
    }
}

// ─── Training Tab ─────────────────────────────────────────────────────────────

@Composable
fun TrainingTab(state: MLUiState, viewModel: MLViewModel) {
    val cs = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Symbol & Interval selector
        item {
            SectionHeader("Target Asset")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SymbolChips(
                    selected = state.symbol,
                    symbols = listOf("BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT","XRPUSDT"),
                    onSelect = viewModel::setSymbol,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text("Interval", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(CandleInterval.FIFTEEN_MINUTES, CandleInterval.ONE_HOUR,
                    CandleInterval.FOUR_HOURS, CandleInterval.ONE_DAY)) { iv ->
                    FilterChip(selected = state.interval == iv,
                        onClick = { viewModel.setInterval(iv) },
                        label = { Text(iv.label) })
                }
            }
        }

        // ── v2.0: Model selection toggles ────────────────────────────────────
        item {
            HigSectionHeader("Model Selection",
                footnote = "Enable/disable models independently")
            Spacer(Modifier.height(AppleSpacing.xs))
            HigGroupedCard {
                HigSwitchRow(
                    title = "Gradient Boosting",
                    subtitle = "XGBoost-style — fast, interpretable, 56 features",
                    checked = state.ensembleConfig.useGradientBoosting,
                    onToggle = { viewModel.toggleUseGB() }
                )
                HigDivider()
                HigSwitchRow(
                    title = "LSTM Neural Network",
                    subtitle = "Sequential memory model — captures temporal patterns",
                    checked = state.ensembleConfig.useLSTM,
                    onToggle = { viewModel.toggleUseLSTM() }
                )
            }
            if (!state.ensembleConfig.useGradientBoosting && !state.ensembleConfig.useLSTM) {
                Spacer(Modifier.height(AppleSpacing.xs))
                Surface(shape = RoundedCornerShape(AppleShapes.sm),
                    color = appleOrange().copy(alpha = 0.1f),
                    border = BorderStroke(0.5.dp, appleOrange().copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp)) {
                    Row(modifier = Modifier.padding(AppleSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, null,
                            tint = appleOrange(), modifier = Modifier.size(16.dp))
                        Text("At least one model must be enabled for predictions",
                            style = MaterialTheme.typography.titleMedium, color = appleOrange())
                    }
                }
            }
        }

        // ── v2.0: Cycle enhancer toggles ─────────────────────────────────────
        item {
            HigSectionHeader("Cycle Enhancers",
                footnote = "Macro cycle features applied to ML inputs")
            Spacer(Modifier.height(AppleSpacing.xs))
            HigGroupedCard {
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🌙", fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Moon Cycle Enhancer",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFFFFD60A), fontWeight = FontWeight.SemiBold)
                        Text("6 lunar features from the 29.53-day synodic cycle",
                            style = MaterialTheme.typography.labelSmall,
                            color = appleSecondaryLabel())
                    }
                    Switch(
                        checked = state.ensembleConfig.useMoonCycleEnhancer,
                        onCheckedChange = { viewModel.toggleMoonEnhancer() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFFFFD60A),
                            uncheckedTrackColor = appleGray().copy(alpha = 0.25f),
                            checkedThumbColor = Color.White,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
                HigDivider()
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("✡️", fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shemitah Cycle Enhancer",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF64D2FF), fontWeight = FontWeight.SemiBold)
                        Text("5 macro features from Torah's 7-year biblical cycle",
                            style = MaterialTheme.typography.labelSmall,
                            color = appleSecondaryLabel())
                    }
                    Switch(
                        checked = state.ensembleConfig.useShemitahEnhancer,
                        onCheckedChange = { viewModel.toggleShemitahEnhancer() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF64D2FF),
                            uncheckedTrackColor = appleGray().copy(alpha = 0.25f),
                            checkedThumbColor = Color.White,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }

        // GB Config card
        item {
            // Moon Cycle feature note — HIG grouped info card
            HigGroupedCard(modifier = Modifier.padding(horizontal = 0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌙", fontSize = 22.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Moon Cycle features included",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFFFFD60A), fontWeight = FontWeight.SemiBold)
                        Text("6 lunar features from the 29.53-day synodic cycle " +
                            "automatically added to all 56 model inputs.",
                            style = MaterialTheme.typography.labelSmall,
                            color = appleSecondaryLabel(), lineHeight = 16.sp)
                    }
                }
            }
        }

        // Shemitah Cycle feature note
        item {
            HigGroupedCard(modifier = Modifier.padding(horizontal = 0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✡️", fontSize = 22.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shemitah Cycle enhancer included",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF64D2FF), fontWeight = FontWeight.SemiBold)
                        Text("5 macro features from the Torah's 7-year biblical cycle (Cahn 2014): " +
                            "Shemitah score, year-in-cycle, bear pressure, bull recovery, " +
                            "Elul 29 proximity. Attached as an enhancer to all 29 frameworks.",
                            style = MaterialTheme.typography.labelSmall,
                            color = appleSecondaryLabel(), lineHeight = 16.sp)
                    }
                }
            }
        }

        // GB Config card
        item {
            ModelConfigCard(
                title = "Gradient Boosting",
                subtitle = "XGBoost-style • ${state.ensembleConfig.gbConfig.nTrees} trees • " +
                    state.ensembleConfig.gbConfig.trainingWindow.label,
                icon = Icons.Filled.AccountTree,
                color = Color(0xFF00B0FF),
                expanded = state.showGbConfig,
                onToggle = viewModel::toggleGbConfig
            ) {
                GBConfigPanel(state.ensembleConfig.gbConfig, viewModel::updateGbConfig)
            }
        }

        // LSTM Config card
        item {
            ModelConfigCard(
                title = "LSTM",
                subtitle = "Seq ${state.ensembleConfig.lstmConfig.sequenceLength} • " +
                    "${state.ensembleConfig.lstmConfig.hiddenUnits}u × " +
                    "${state.ensembleConfig.lstmConfig.numLayers}L • " +
                    "${state.ensembleConfig.lstmConfig.epochs} epochs • " +
                    state.ensembleConfig.lstmConfig.trainingWindow.label,
                icon = Icons.Filled.Timeline,
                color = Color(0xFFBC8CFF),
                expanded = state.showLstmConfig,
                onToggle = viewModel::toggleLstmConfig
            ) {
                LSTMConfigPanel(state.ensembleConfig.lstmConfig, viewModel::updateLstmConfig)
            }
        }

        // Ensemble weights
        item {
            EnsembleWeightsCard(state.ensembleConfig, viewModel::updateEnsembleWeights)
        }

        // Training progress
        if (state.progress.status != TrainingStatus.IDLE) {
            item { TrainingProgressCard(state.progress) }
        }

        // Train / Cancel button — reflects which models are enabled
        item {
            val useGB   = state.ensembleConfig.useGradientBoosting
            val useLSTM = state.ensembleConfig.useLSTM
            val noneEnabled = !useGB && !useLSTM

            // Build a label that reflects exactly what will be trained
            val trainLabel = when {
                useGB && useLSTM -> "Train GB + LSTM  •  ${state.symbol} ${state.interval.label}"
                useGB            -> "Train Gradient Boosting  •  ${state.symbol} ${state.interval.label}"
                useLSTM          -> "Train LSTM  •  ${state.symbol} ${state.interval.label}"
                else             -> "Enable a model above to train"
            }

            if (viewModel.isTraining) {
                // Show which model(s) are currently training based on progress status
                val trainingLabel = when (state.progress.status) {
                    TrainingStatus.TRAINING_GB   -> "Training Gradient Boosting…"
                    TrainingStatus.TRAINING_LSTM -> "Training LSTM…"
                    TrainingStatus.FETCHING_DATA -> "Fetching data…"
                    TrainingStatus.PREPROCESSING -> "Preprocessing features…"
                    TrainingStatus.BACKTESTING   -> "Running backtest…"
                    TrainingStatus.COMBINING     -> "Combining models…"
                    else                         -> "Training…"
                }
                OutlinedButton(
                    onClick = viewModel::cancelTraining,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(trainingLabel, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = viewModel::startTraining,
                    enabled = !noneEnabled,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            noneEnabled     -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            useGB && useLSTM -> Color(0xFF007AFF)   // both — system blue
                            useGB           -> Color(0xFF00B0FF)   // GB only — lighter blue
                            else            -> Color(0xFFBC8CFF)   // LSTM only — purple
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(trainLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Model info after training
        state.modelInfo?.let { info ->
            item { ModelInfoCard(info) }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun SymbolChips(selected: String, symbols: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(symbols) { sym ->
            FilterChip(selected = selected == sym,
                onClick = { onSelect(sym) },
                label = { Text(sym.removeSuffix("USDT"), fontSize = 12.sp) })
        }
    }
}

@Composable
fun ModelConfigCard(
    title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
                    HorizontalDivider(); Spacer(Modifier.height(10.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun GBConfigPanel(cfg: GBConfig, onSave: (GBConfig) -> Unit) {
    var nTrees by remember { mutableIntStateOf(cfg.nTrees) }
    var maxDepth by remember { mutableIntStateOf(cfg.maxDepth) }
    var window by remember { mutableStateOf(cfg.trainingWindow) }
    var useFramework by remember { mutableStateOf(cfg.useFrameworkFeatures) }

    ConfigSlider("Trees", nTrees.toFloat(), 20f, 300f) { nTrees = it.toInt() }
    ConfigSlider("Max Depth", maxDepth.toFloat(), 2f, 10f) { maxDepth = it.toInt() }
    Spacer(Modifier.height(6.dp))
    Text("Training Window", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TrainingWindow.values().forEach { tw ->
            FilterChip(selected = window == tw, onClick = { window = tw },
                label = { Text(tw.label, fontSize = 11.sp) })
        }
    }
    Spacer(Modifier.height(6.dp))
    FrameworkToggle("Incorporate 27 Framework Features", useFramework) { useFramework = it }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { onSave(cfg.copy(nTrees = nTrees, maxDepth = maxDepth, trainingWindow = window,
        useFrameworkFeatures = useFramework)) }, modifier = Modifier.fillMaxWidth()) { Text("Save GB Config") }
}

@Composable
fun LSTMConfigPanel(cfg: LSTMConfig, onSave: (LSTMConfig) -> Unit) {
    var seqLen by remember { mutableIntStateOf(cfg.sequenceLength) }
    var hidden by remember { mutableIntStateOf(cfg.hiddenUnits) }
    var layers by remember { mutableIntStateOf(cfg.numLayers) }
    var epochs by remember { mutableIntStateOf(cfg.epochs) }
    var window by remember { mutableStateOf(cfg.trainingWindow) }
    var useFramework by remember { mutableStateOf(cfg.useFrameworkFeatures) }

    ConfigSlider("Sequence Length", seqLen.toFloat(), 20f, 120f) { seqLen = it.toInt() }
    ConfigSlider("Hidden Units",    hidden.toFloat(),  16f, 256f) { hidden = it.toInt() }
    ConfigSlider("LSTM Layers",     layers.toFloat(),  1f,  4f)   { layers = it.toInt() }
    ConfigSlider("Epochs",          epochs.toFloat(),  10f, 200f) { epochs = it.toInt() }
    Spacer(Modifier.height(6.dp))
    Text("Training Window", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(TrainingWindow.values().toList()) { tw ->
            FilterChip(selected = window == tw, onClick = { window = tw },
                label = { Text(tw.label, fontSize = 11.sp) })
        }
    }
    Spacer(Modifier.height(6.dp))
    FrameworkToggle("Incorporate 27 Framework Features", useFramework) { useFramework = it }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { onSave(cfg.copy(sequenceLength = seqLen, hiddenUnits = hidden,
        numLayers = layers, epochs = epochs, trainingWindow = window,
        useFrameworkFeatures = useFramework)) }, modifier = Modifier.fillMaxWidth()) { Text("Save LSTM Config") }
}

@Composable
fun ConfigSlider(label: String, value: Float, min: Float, max: Float, onValue: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (value == value.toInt().toFloat()) value.toInt().toString()
                else "%.3f".format(value),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
        Slider(value = value, onValueChange = onValue, valueRange = min..max,
            modifier = Modifier.height(32.dp))
    }
}

@Composable
fun FrameworkToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("Preprocesses candles through all 27 TA frameworks before ML input",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun EnsembleWeightsCard(cfg: EnsembleConfig, onSave: (Double, Double, Double, Double) -> Unit) {
    var gbW by remember { mutableStateOf(cfg.gbWeight.toFloat()) }
    var lstmW by remember { mutableStateOf(cfg.lstmWeight.toFloat()) }
    var disagree by remember { mutableStateOf(cfg.disagreementThreshold.toFloat()) }
    var minConf by remember { mutableStateOf(cfg.minConfidenceForSignal.toFloat()) }

    Card(shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Balance, null, tint = Color(0xFFFFD600),
                    modifier = Modifier.size(20.dp))
                Text("Ensemble Weights", fontWeight = FontWeight.SemiBold)
            }
            Text("If GB and LSTM disagree by >${"%.0f".format(disagree*100)}% → HOLD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            ConfigSlider("GB Weight",  gbW,      0.1f, 0.9f) { gbW = it; lstmW = 1f - it }
            ConfigSlider("LSTM Weight", lstmW,   0.1f, 0.9f) { lstmW = it; gbW = 1f - it }
            ConfigSlider("Disagree Threshold", disagree, 0.05f, 0.5f) { disagree = it }
            ConfigSlider("Min Confidence", minConf, 0.3f, 0.9f) { minConf = it }
            Button(onClick = { onSave(gbW.toDouble(), lstmW.toDouble(),
                disagree.toDouble(), minConf.toDouble()) },
                modifier = Modifier.fillMaxWidth()) { Text("Save Ensemble Config") }
        }
    }
}

@Composable
fun TrainingProgressCard(progress: TrainingProgress) {
    val cs = MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(statusLabel(progress.status), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${progress.progressPct}%", fontWeight = FontWeight.Bold,
                    color = cs.primary, fontSize = 14.sp)
            }
            LinearProgressIndicator(
                progress = { progress.progressPct / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            when (progress.status) {
                TrainingStatus.TRAINING_GB -> {
                    Text("Trees: ${progress.currentEpoch}/${progress.totalEpochs}  •  " +
                         "Loss: ${"%.4f".format(progress.trainLoss)}  •  " +
                         "Acc: ${"%.1f".format(progress.trainAccuracy * 100)}%",
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                TrainingStatus.TRAINING_LSTM -> {
                    Text("Epoch: ${progress.currentEpoch}/${progress.totalEpochs}  •  " +
                         "Train: ${"%.4f".format(progress.trainLoss)}  •  " +
                         "Val: ${"%.4f".format(progress.valLoss)}  •  " +
                         "Acc: ${"%.1f".format(progress.valAccuracy * 100)}%",
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                TrainingStatus.READY -> {
                    Text("✅ Training complete!  Win Rate: ${"%.1f".format(progress.backtestWinRate * 100)}%  •  " +
                         "P&L: ${"%.1f".format(progress.backtestPnl)}%",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF3FB950),
                        fontWeight = FontWeight.Medium)
                }
                TrainingStatus.FAILED -> {
                    Text("❌ ${progress.errorMessage}", style = MaterialTheme.typography.labelSmall,
                        color = cs.error)
                }
                else -> {
                    if (progress.totalCandles > 0)
                        Text("${progress.totalCandles} candles loaded",
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            if (progress.elapsedMs > 0) {
                Text("Elapsed: ${progress.elapsedMs / 1000}s",
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ModelInfoCard(info: SavedModelInfo) {
    val fmt = SimpleDateFormat("MMM d HH:mm", Locale.getDefault())
    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Model Summary", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("${info.symbol} ${info.interval}", Modifier.weight(1f))
                InfoChip("${info.totalSamples} samples", Modifier.weight(1f))
                InfoChip("${info.featureCount} features", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("Win ${"%.0f".format(info.ensembleAccuracy * 100)}%", Modifier.weight(1f))
                InfoChip(info.trainingWindow.label, Modifier.weight(1f))
                InfoChip(fmt.format(Date(info.trainedAt)), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, modifier = Modifier.padding(6.dp).fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center, maxLines = 1)
    }
}

// ─── Predictions Tab ──────────────────────────────────────────────────────────

@Composable
fun PredictionsTab(state: MLUiState) {
    if (state.predictionHistory.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Psychology, null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Text("Train a model to see predictions",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("${state.predictionHistory.size} predictions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.predictionHistory) { pred ->
                PredictionCard(pred)
            }
        }
    }
}

@Composable
fun PredictionCard(pred: EnsemblePrediction) {
    val cs = MaterialTheme.colorScheme
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val (signalColor) = when (pred.signal) {
        SignalType.STRONG_BUY  -> listOf(Color(0xFF00E676))
        SignalType.BUY         -> listOf(Color(0xFF26A69A))
        SignalType.SELL        -> listOf(Color(0xFFFF6D00))
        SignalType.STRONG_SELL -> listOf(Color(0xFFFF1744))
        SignalType.NEUTRAL     -> listOf(cs.onSurfaceVariant)
    }

    Card(shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, cs.outlineVariant)) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(signalColor, CircleShape))
                    Text(pred.signal.name.replace('_', ' '),
                        fontWeight = FontWeight.Bold, color = signalColor)
                    Text("@ ${"%.2f".format(pred.price)}", fontSize = 12.sp,
                        color = cs.onSurfaceVariant)
                }
                Text(fmt.format(Date(pred.timestamp)), style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant)
            }
            // Probability bars
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProbBar("Buy",  pred.buyProbability,  Color(0xFF00E676), Modifier.weight(1f))
                ProbBar("Hold", pred.holdProbability, cs.onSurfaceVariant.copy(alpha = 0.5f), Modifier.weight(1f))
                ProbBar("Sell", pred.sellProbability, Color(0xFFFF1744), Modifier.weight(1f))
            }
            // Model agreement
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Confidence: ${"%.0f".format(pred.confidence * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBC8CFF))
                Text("Agreement: ${"%.0f".format(pred.agreement * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pred.agreement > 0.7) Color(0xFF3FB950) else cs.onSurfaceVariant)
            }
            if (pred.notes.isNotBlank()) {
                Text(pred.notes, style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
fun ProbBar(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(modifier = Modifier
                .fillMaxWidth((value / 100.0).toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color))
        }
        Text("$label ${"%.0f".format(value)}%", fontSize = 10.sp,
            color = color, fontWeight = FontWeight.Medium)
    }
}

// ─── Feature Importance Tab ───────────────────────────────────────────────────

@Composable
fun FeaturesTab(state: MLUiState) {
    if (state.featureImportance.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppleSpacing.sm)
            ) {
                Icon(Icons.Outlined.Functions, null, modifier = Modifier.size(48.dp),
                    tint = appleGray().copy(alpha = 0.3f))
                Text("No Feature Data",
                    style = MaterialTheme.typography.headlineMedium, color = appleLabel())
                Text("Train a model to see feature importance",
                    style = MaterialTheme.typography.titleMedium, color = appleSecondaryLabel())
            }
        }
    } else {
        val maxVal = state.featureImportance.maxOfOrNull { it.second } ?: 1.0
        val lunarFeatures = setOf("lunar_age_norm","lunar_illumination","lunar_momentum",
            "lunar_reversal_prox","new_moon_window","full_moon_window")

        LazyColumn(
            contentPadding = PaddingValues(vertical = AppleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppleSpacing.xl)
        ) {
            // Header card
            item {
                HigGroupedCard(modifier = Modifier.padding(horizontal = AppleSpacing.base)) {
                    HigListRow(
                        title = "56 Features • 29 Frameworks + Lunar + Shemitah",
                        subtitle = "6 Moon Cycle + 5 Shemitah macro features (v1.9)",
                        leadingIcon = Icons.Filled.Functions,
                        leadingIconColor = appleBlue()
                    )
                    HigDivider()
                    HigListRow(
                        title = "Feature Importance",
                        subtitle = "Ranked by Gradient Boosting information gain",
                        leadingIcon = Icons.Filled.BarChart,
                        leadingIconColor = appleGreen()
                    )
                }
            }

            // Shemitah features section (highlighted at top — Torah cycle)
            val shemitahFeatureIds = setOf("shemitah_score","shemitah_year_norm",
                "shemitah_bear_pressure","shemitah_bull_recovery","elul_proximity")
            val shemitahImportance = state.featureImportance.filter { it.first in shemitahFeatureIds }
            if (shemitahImportance.isNotEmpty()) {
                item {
                    HigSectionHeader("Shemitah Cycle Features",
                        footnote = "5 macro features from the 7-year biblical cycle (Cahn 2014)")
                    Spacer(Modifier.height(AppleSpacing.xs))
                    HigGroupedCard {
                        shemitahImportance.forEachIndexed { i, (name, importance) ->
                            HigShemitahFeatureRow(name, importance, maxVal)
                            if (i < shemitahImportance.size - 1) HigDivider()
                        }
                    }
                }
            }

            // Lunar features section (highlighted separately at top)
            val lunarImportance = state.featureImportance.filter { it.first in lunarFeatures }
            if (lunarImportance.isNotEmpty()) {
                item {
                    HigSectionHeader("Moon Cycle Features",
                        footnote = "6 lunar features from the 29.53-day synodic cycle")
                    Spacer(Modifier.height(AppleSpacing.xs))
                    HigGroupedCard {
                        lunarImportance.forEachIndexed { i, (name, importance) ->
                            HigLunarFeatureRow(name, importance, maxVal)
                            if (i < lunarImportance.size - 1) HigDivider()
                        }
                    }
                }
            }

            // All features ranked
            item {
                HigSectionHeader("All Features — Ranked by Importance")
                Spacer(Modifier.height(AppleSpacing.xs))
                HigGroupedCard {
                    state.featureImportance.forEachIndexed { i, (name, importance) ->
                        val isLunar    = name in lunarFeatures
                        val isShemitah = name in shemitahFeatureIds
                        HigFeatureRow(name, importance, maxVal, isLunar, isShemitah)
                        if (i < state.featureImportance.size - 1) HigDivider()
                    }
                }
            }

            item { Spacer(Modifier.height(AppleSpacing.xl)) }
        }
    }
}

@Composable
fun HigLunarFeatureRow(name: String, importance: Double, max: Double) {
    val frac = (importance / max.coerceAtLeast(0.001)).toFloat()
    val moonColor = Color(0xFFFFD60A)  // gold for lunar features

    // Map feature code name to human-readable label + description
    val (label, desc) = when (name) {
        "lunar_age_norm"     -> "Lunar Age (norm.)" to "Days since New Moon, 0–1 scale"
        "lunar_illumination" -> "Illumination %" to "Moon surface lit fraction"
        "lunar_momentum"     -> "Lunar Momentum" to "sin(φ): +1 waxing, -1 waning"
        "lunar_reversal_prox"-> "Reversal Proximity" to "1.0 at exact New/Full Moon"
        "new_moon_window"    -> "New Moon Window" to "1.0 within ±1.85d of New Moon"
        "full_moon_window"   -> "Full Moon Window" to "1.0 within ±1.85d of Full Moon"
        else                  -> name.replace('_', ' ') to ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🌙", fontSize = 18.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(label,
                style = MaterialTheme.typography.headlineSmall,
                color = moonColor, fontWeight = FontWeight.Medium)
            if (desc.isNotEmpty()) Text(desc,
                style = MaterialTheme.typography.labelSmall,
                color = appleSecondaryLabel())
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier.width(80.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(appleGray().copy(alpha = 0.15f))
            ) {
                Box(modifier = Modifier.fillMaxWidth(frac).fillMaxHeight()
                    .background(moonColor))
            }
            Text("${"%.1f".format(importance * 100)}%",
                fontSize = 11.sp, color = moonColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun HigShemitahFeatureRow(name: String, importance: Double, max: Double) {
    val frac = (importance / max.coerceAtLeast(0.001)).toFloat()
    val shemColor = Color(0xFF64D2FF)  // systemTeal — distinct from gold (lunar) and green (TA)
    val (label, desc) = when (name) {
        "shemitah_score"         -> "Shemitah Score" to "Composite macro bias ∈ [−1,+1] (Yr7=−1, Yr1=+0.9)"
        "shemitah_year_norm"     -> "Shemitah Year (norm.)" to "Year-in-cycle 1–7, normalised 0–1"
        "shemitah_bear_pressure" -> "Bear Pressure" to "0→1: active Shemitah bearish macro pressure"
        "shemitah_bull_recovery" -> "Bull Recovery" to "0→1: post-Shemitah recovery signal"
        "elul_proximity"         -> "Elul 29 Proximity" to "0→1 in 30-day window before ~Sep 13"
        else                      -> name.replace('_', ' ') to ""
    }
    Row(modifier = Modifier.fillMaxWidth()
        .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
        verticalAlignment = Alignment.CenterVertically) {
        Text("✡️", fontSize = 16.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.headlineSmall,
                color = shemColor, fontWeight = FontWeight.Medium)
            if (desc.isNotEmpty()) Text(desc, style = MaterialTheme.typography.labelSmall,
                color = appleSecondaryLabel())
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(modifier = Modifier.width(80.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(appleGray().copy(alpha = 0.15f))) {
                Box(modifier = Modifier.fillMaxWidth(frac).fillMaxHeight().background(shemColor))
            }
            Text("${"%.1f".format(importance * 100)}%",
                fontSize = 11.sp, color = shemColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun HigFeatureRow(name: String, importance: Double, max: Double,
                  isLunar: Boolean = false, isShemitah: Boolean = false) {
    val frac = (importance / max.coerceAtLeast(0.001)).toFloat()
    val barColor = when {
        isShemitah -> Color(0xFF64D2FF)
        isLunar    -> Color(0xFFFFD60A)
        frac > 0.7f -> appleGreen()
        frac > 0.4f -> appleBlue()
        frac > 0.2f -> applePurple()
        else -> appleGray()
    }
    val icon = when {
        isShemitah -> "✡️"
        isLunar    -> "🌙"
        else       -> "  "
    }
    Row(modifier = Modifier.fillMaxWidth()
        .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md)) {
        Text(icon, fontSize = 11.sp, modifier = Modifier.width(14.dp))
        Text(name.replace('_', ' '), modifier = Modifier.width(130.dp),
            style = MaterialTheme.typography.labelMedium, color = barColor, maxLines = 1)
        Box(modifier = Modifier.weight(1f).height(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(appleGray().copy(alpha = 0.12f))) {
            Box(modifier = Modifier.fillMaxWidth(frac).fillMaxHeight()
                .clip(RoundedCornerShape(2.dp)).background(barColor.copy(alpha = 0.85f)))
        }
        Text("${"%.1f".format(importance * 100)}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(36.dp), textAlign = TextAlign.End, color = barColor)
    }
}

@Composable
fun FeatureRow(name: String, importance: Double, max: Double) {
    HigFeatureRow(name, importance, max)
}

// ─── Backtest Tab ─────────────────────────────────────────────────────────────

@Composable
fun BacktestTab(state: MLUiState, @Suppress("UNUSED_PARAMETER") viewModel: MLViewModel) {
    val progress = state.progress
    if (progress.status != TrainingStatus.READY && state.modelInfo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.BarChart, null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Text("Train a model to see backtest results",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary metrics grid
        item { BacktestSummary(progress) }

        // Trade-level results
        if (progress.status == TrainingStatus.READY) {
            item {
                Text("Backtest Methodology",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                MethodologyCard()
            }

            item {
                Text("Signal Quality by Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                SignalQualityGrid(progress)
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Performance Over Time",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(6.dp),
                        color = if (progress.backtestPnl > 0)
                            Color(0xFF3FB950).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            (if (progress.backtestPnl > 0) "+" else "") +
                            "${"%.2f".format(progress.backtestPnl)}% total",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progress.backtestPnl > 0) Color(0xFF3FB950)
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                PnlEquityCurve(progress)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun MethodologyCard() {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MethodBadge("Hold-out: Last 20%")
                MethodBadge("Time-ordered splits")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MethodBadge("No lookahead bias")
                MethodBadge("Lookahead: 3 bars")
            }
            Text(
                "⚠️ Backtest results on training data. Past performance does not guarantee future results. " +
                "Always use proper risk management and position sizing.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun MethodBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun SignalQualityGrid(progress: TrainingProgress) {
    val cs = MaterialTheme.colorScheme
    val winRate = progress.backtestWinRate
    val pnl = progress.backtestPnl

    Card(shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, cs.outlineVariant)) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Top row: Win Rate, P&L, Ensemble Accuracy
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell(
                    "Win Rate",
                    "${"%.1f".format(winRate * 100)}%",
                    if (winRate > 0.6) Color(0xFF3FB950)
                    else if (winRate > 0.5) Color(0xFFE3B341)
                    else cs.error,
                    if (winRate > 0.6) "Strong" else if (winRate > 0.5) "Moderate" else "Weak",
                    Modifier.weight(1f)
                )
                MetricCell(
                    "Net P&L",
                    "${"%.1f".format(pnl)}%",
                    if (pnl > 0) Color(0xFF3FB950) else cs.error,
                    if (pnl > 5) "Excellent" else if (pnl > 0) "Positive" else "Negative",
                    Modifier.weight(1f)
                )
                MetricCell(
                    "Ensemble Acc",
                    "${"%.1f".format(progress.ensembleAccuracy * 100)}%",
                    Color(0xFFBC8CFF),
                    "GB+LSTM",
                    Modifier.weight(1f)
                )
            }

            HorizontalDivider(thickness = 0.5.dp)

            // Model breakdown
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell(
                    "GB Accuracy",
                    "${"%.1f".format(progress.gbAccuracy * 100)}%",
                    Color(0xFF00B0FF), "Trees", Modifier.weight(1f)
                )
                MetricCell(
                    "LSTM Accuracy",
                    "${"%.1f".format(progress.valAccuracy * 100)}%",
                    Color(0xFFBC8CFF), "Temporal", Modifier.weight(1f)
                )
                MetricCell(
                    "Features",
                    "45",
                    Color(0xFF69F0AE), "27 frameworks", Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MetricCell(label: String, value: String, color: Color, sub: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color,
                textAlign = TextAlign.Center)
            Text(sub, fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun PnlEquityCurve(progress: TrainingProgress) {
    // Simulated equity curve visualization using progress data
    val cs = MaterialTheme.colorScheme
    val winRate = progress.backtestWinRate
    val pnl = progress.backtestPnl

    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = BorderStroke(0.5.dp, cs.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Simulated equity bars (10-bar summary)
            val bars = 10
            val simulatedReturns = List(bars) { i ->
                val trend = pnl / bars
                val noise = (i % 3 - 1) * 0.5 * kotlin.math.abs(pnl) / bars
                trend + noise
            }
            var cumulative = 0.0
            val cumReturns = simulatedReturns.map { r -> cumulative += r; cumulative }
            val maxCum = cumReturns.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val minCum = cumReturns.minOrNull()?.takeIf { it < 0 } ?: -1.0
            val range = maxOf(maxCum - minCum, 1.0)

            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                cumReturns.forEach { ret ->
                    val frac = ((ret - minCum) / range).toFloat().coerceIn(0.05f, 1f)
                    val barColor = if (ret >= 0) Color(0xFF3FB950) else cs.error
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(frac)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(barColor.copy(alpha = 0.7f))
                    )
                }
            }

            // Labels
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start", fontSize = 9.sp, color = cs.onSurfaceVariant)
                Text("← Backtested periods →", fontSize = 9.sp, color = cs.onSurfaceVariant)
                Text("End", fontSize = 9.sp, color = cs.onSurfaceVariant)
            }

            Text(
                "Equity curve approximation based on ${("%.1f".format(winRate * 100))}% win rate over backtest period",
                fontSize = 9.sp, color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BacktestSummary(progress: TrainingProgress) {
    val cs = MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Backtest Results", fontWeight = FontWeight.SemiBold)
            Text("Hold-out: last 20% of training data (chronological)",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BacktestMetricCard("Win Rate",
                    "${"%.1f".format(progress.backtestWinRate * 100)}%",
                    if (progress.backtestWinRate > 0.55) Color(0xFF3FB950) else cs.error,
                    Modifier.weight(1f))
                BacktestMetricCard("Total P&L",
                    "${"%.1f".format(progress.backtestPnl)}%",
                    if (progress.backtestPnl > 0) Color(0xFF3FB950) else cs.error,
                    Modifier.weight(1f))
                BacktestMetricCard("Ensemble Acc",
                    "${"%.1f".format(progress.ensembleAccuracy * 100)}%",
                    Color(0xFFBC8CFF), Modifier.weight(1f))
            }
            Text("Note: Past backtest accuracy does not guarantee future performance.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
    }
}

@Composable
fun BacktestMetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

fun statusLabel(s: TrainingStatus) = when (s) {
    TrainingStatus.IDLE          -> "Ready to Train"
    TrainingStatus.FETCHING_DATA -> "📡 Fetching Historical Data"
    TrainingStatus.PREPROCESSING -> "⚙️ Feature Engineering (27 Frameworks)"
    TrainingStatus.TRAINING_GB   -> "🌲 Training Gradient Boosting"
    TrainingStatus.TRAINING_LSTM -> "🧠 Training LSTM"
    TrainingStatus.COMBINING     -> "🔗 Combining Models"
    TrainingStatus.BACKTESTING   -> "📊 Running Backtest"
    TrainingStatus.READY         -> "✅ Model Ready"
    TrainingStatus.FAILED        -> "❌ Training Failed"
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
