package com.chartanalyzer.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chartanalyzer.app.models.*
import com.chartanalyzer.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

val PRESET_QUESTIONS = listOf(
    "Full technical analysis using all active frameworks",
    "Identify the Elliott Wave count and next targets",
    "Find all confluence zones and rate their strength",
    "Detect all divergence signals (regular + hidden)",
    "Scan for harmonic patterns and measure validity",
    "Map order blocks, FVGs, and liquidity pools",
    "Bollinger squeeze status + MACD momentum",
    "Identify smart money levels and structural breaks",
    "Multi-timeframe trend alignment score",
    "Best entry zone with stop loss and targets"
)

// ─── Root App Shell ──────────────────────────────────────────────────────────
// HIG: Tab bar at bottom, ≤5 tabs per iPhone guidelines (we use 7 — truncate labels)
// HIG: Navigation bar with Large Title scrolls into inline title on scroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartAnalyzerApp() {
    val viewModel: ChartAnalyzerViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var marketDataProvider by remember { mutableStateOf(MarketDataProvider.BINANCE) }
    val tabs = listOf("Analyze", "Markets", "ML", "Portfolio", "Frameworks", "History")

    if (uiState.showApiKeyDialog) {
        ApiKeySheet(currentKey = uiState.apiKey,
            onConfirm = viewModel::setApiKey, onDismiss = viewModel::dismissApiKeyDialog)
    }
    if (uiState.showProviderSheet) {
        AiProviderSheet(state = uiState,
            onSelectProvider = viewModel::selectProvider,
            onSaveCredential = viewModel::saveCredential,
            onDismiss = viewModel::dismissProviderSheet)
    }

    // Intercept system back button when Settings is open
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Main app scaffold ─────────────────────────────────────────────────
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.xs)) {
                            Icon(Icons.AutoMirrored.Filled.ShowChart, null,
                                tint = appleBlue(), modifier = Modifier.size(20.dp))
                            Text("ChartAnalyzer",
                                style = MaterialTheme.typography.headlineMedium)
                            Text("v2.0",
                                fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                color = appleSecondaryLabel())
                        }
                    },
                    actions = {
                        // AI provider button
                        IconButton(onClick = viewModel::showProviderSheet,
                            modifier = Modifier.size(AppleSpacing.xxxl)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val provider = uiState.activeProvider
                                if (provider != null) {
                                    Text(provider.emoji, fontSize = 15.sp, lineHeight = 17.sp)
                                    Text("AI", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                        color = if (uiState.isActiveProviderReady) appleBlue()
                                                else appleRed(), lineHeight = 10.sp)
                                } else {
                                    Icon(Icons.Outlined.Key, "API Keys",
                                        tint = appleRed(), modifier = Modifier.size(18.dp))
                                    Text("AI", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                        color = appleRed())
                                }
                            }
                        }
                        // Settings cog icon
                        IconButton(onClick = { showSettings = true },
                            modifier = Modifier.size(AppleSpacing.xxxl)) {
                            Icon(Icons.Outlined.Settings, "Settings",
                                tint = appleGray(), modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = appleSecondaryGroupedBackground(),
                        titleContentColor = appleLabel()
                    ),
                    windowInsets = WindowInsets.statusBars
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = appleSecondaryGroupedBackground(),
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        val icon = when (index) {
                            0 -> if (isSelected) Icons.Filled.Analytics else Icons.Outlined.Analytics
                            1 -> if (isSelected) Icons.Filled.CandlestickChart else Icons.Outlined.CandlestickChart
                            2 -> if (isSelected) Icons.Filled.Psychology else Icons.Outlined.Psychology
                            3 -> if (isSelected) Icons.Filled.AccountBalance else Icons.Outlined.AccountBalance
                            4 -> if (isSelected) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Outlined.List
                            else -> if (isSelected) Icons.Filled.History else Icons.Outlined.History
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, tab, modifier = Modifier.size(22.dp)) },
                            label = { Text(tab, fontSize = 10.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = appleBlue(),
                                selectedTextColor = appleBlue(),
                                indicatorColor = appleBlue().copy(alpha = 0.12f),
                                unselectedIconColor = appleGray(),
                                unselectedTextColor = appleGray()
                            )
                        )
                    }
                }
            },
            containerColor = appleGroupedBackground()
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> AnalyzeTab(uiState, viewModel, onNavigateToFrameworks = { selectedTab = 4 })
                    1 -> BinanceMarketTab()
                    2 -> MLPredictionScreen()
                    3 -> PortfolioScreen()
                    4 -> FrameworksTab(uiState, viewModel)
                    5 -> HistoryTab(uiState)
                }
            }
        }

        // ── Settings overlay — full screen, on top of everything ─────────────
        // Rendered outside the Scaffold so it covers topbar + bottom nav too.
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit  = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = appleGroupedBackground()
            ) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)) {

                    // Settings top bar with back + close buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appleSecondaryGroupedBackground())
                            .padding(horizontal = AppleSpacing.xs, vertical = AppleSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ← Back button
                        IconButton(
                            onClick = { showSettings = false },
                            modifier = Modifier.size(AppleSpacing.xxxl)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                                tint = appleBlue(), modifier = Modifier.size(20.dp))
                        }
                        // Title
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = appleLabel(),
                            modifier = Modifier.weight(1f)
                        )
                        // × Close button (additional affordance)
                        IconButton(
                            onClick = { showSettings = false },
                            modifier = Modifier.size(AppleSpacing.xxxl)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(appleGray().copy(alpha = 0.2f),
                                        androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, "Close Settings",
                                    tint = appleLabel(),
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))

                    // Settings content — weight(1f) gives LazyColumn a bounded height
                    // so it scrolls correctly and isn't cut off at the bottom.
                    // navigationBarsPadding ensures content clears the gesture bar.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        SettingsScreen(
                            currentProvider = marketDataProvider,
                            onProviderChange = { marketDataProvider = it },
                            onBack = { showSettings = false }
                        )
                    }
                }
            }
        }
    } // end Box
}

// ─── Analyze Tab ──────────────────────────────────────────────────────────────
// HIG: Inset grouped list layout — like iOS Settings
// HIG: Sections with header labels in UPPERCASE secondary color

@Composable
fun AnalyzeTab(
    uiState: AnalysisUiState,
    viewModel: ChartAnalyzerViewModel,
    onNavigateToFrameworks: () -> Unit = {}
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.setChartFromUri(it) } }

    // Track chart source mode
    var showUploadZone   by remember { mutableStateOf(false) }
    var selectedTicker   by remember { mutableStateOf("") }
    var showTickerSearch by remember { mutableStateOf(false) }

    // Hoisted here — NOT inside item{} — so recomposition of LazyColumn items
    // never resets these, preventing crashes when the dropdown opens
    var showQuestionsMenu by remember { mutableStateOf(false) }
    var showSaveDialog    by remember { mutableStateOf(false) }

    if (showTickerSearch) {
        TickerSelectorDialog(
            onSelect = { sym ->
                selectedTicker = sym
                showTickerSearch = false
                showUploadZone = false
                viewModel.clearChart()
            },
            onDismiss = { showTickerSearch = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = AppleSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppleSpacing.xl)
    ) {
        // ── Frameworks summary — tap navigates to Frameworks tab (1c) ─────
        item {
            HigSectionHeader("Active Frameworks")
            Spacer(Modifier.height(AppleSpacing.xs))
            HigGroupedCard {
                HigListRow(
                    title = "${uiState.activeFrameworkIds.size} of ${TechnicalFrameworks.all.size} frameworks active",
                    subtitle = "Tap to customize frameworks & formulas",
                    leadingIcon = Icons.Filled.Tune,
                    leadingIconColor = appleBlue(),
                    showChevron = true,
                    onClick = onNavigateToFrameworks
                )
            }
        }

        // ── Chart Source — two side-by-side buttons (1d) ──────────────────
        item {
            HigSectionHeader("Chart")
            Spacer(Modifier.height(AppleSpacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.base),
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm)
            ) {
                // Left: live ticker chart
                val tickerActive = !showUploadZone && selectedTicker.isNotBlank()
                Button(
                    onClick = { showTickerSearch = true },
                    modifier = Modifier.weight(1f).height(AppleSpacing.xxxl),
                    shape = RoundedCornerShape(AppleShapes.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tickerActive) appleBlue()
                                         else appleGray().copy(alpha = 0.12f),
                        contentColor   = if (tickerActive) Color.White else appleBlue()
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Outlined.CandlestickChart, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (selectedTicker.isNotBlank()) selectedTicker.removeSuffix("USDT")
                        else "Select Ticker",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                }
                // Right: upload image
                Button(
                    onClick = {
                        showUploadZone = true
                        selectedTicker = ""
                        viewModel.clearChart()
                    },
                    modifier = Modifier.weight(1f).height(AppleSpacing.xxxl),
                    shape = RoundedCornerShape(AppleShapes.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showUploadZone) appleBlue()
                                         else appleGray().copy(alpha = 0.12f),
                        contentColor   = if (showUploadZone) Color.White else appleBlue()
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("☝️", fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("Upload Image", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }

        // ── Chart display area ─────────────────────────────────────────────
        item {
            when {
                uiState.chartBitmap != null -> {
                    HigChartImageCard(
                        bitmap = uiState.chartBitmap,
                        onRemove = { viewModel.clearChart(); showUploadZone = true },
                        modifier = Modifier.padding(horizontal = AppleSpacing.base)
                    )
                }
                selectedTicker.isNotBlank() -> {
                    // Embedded live chart WebView for the selected ticker
                    AnalyzeChartWebView(
                        symbol = selectedTicker,
                        viewModel = viewModel,
                        modifier = Modifier
                            .padding(horizontal = AppleSpacing.base)
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
                showUploadZone -> {
                    // Collapsible upload well — shown only when upload is active
                    HigUploadZone(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.padding(horizontal = AppleSpacing.base)
                    )
                }
            }
        }

        // ── Your Question — with Quick Questions button on right ───────────
        item {
            // Save question dialog — shown from hoisted state
            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Save Question") },
                    text = {
                        Text("Save \"${uiState.question.take(60)}…\" as a Quick Question?",
                            style = MaterialTheme.typography.headlineSmall)
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.saveCustomQuestion(uiState.question)
                            showSaveDialog = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = appleBlue())) {
                            Text("Save", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveDialog = false }) {
                            Text("Cancel", color = appleBlue())
                        }
                    },
                    containerColor = appleSecondaryGroupedBackground(),
                    shape = RoundedCornerShape(AppleShapes.lg)
                )
            }

            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppleSpacing.base),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR QUESTION".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = appleSecondaryLabel(), letterSpacing = 0.5.sp)
                // Quick Questions button — far right of header
                Box {
                    TextButton(onClick = { showQuestionsMenu = true },
                        contentPadding = PaddingValues(horizontal = AppleSpacing.xs)) {
                        Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Quick", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(
                        expanded = showQuestionsMenu,
                        onDismissRequest = { showQuestionsMenu = false },
                        modifier = Modifier
                            .background(appleSecondaryGroupedBackground())
                            .heightIn(max = 320.dp)
                            .widthIn(min = 260.dp)
                    ) {
                        // DropdownMenuItems placed directly — no nested scroll wrapper.
                        // DropdownMenu handles its own scrolling when heightIn is set.
                        uiState.savedQuestions.forEachIndexed { idx, q ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Numbered badge
                                        Box(
                                            Modifier
                                                .size(20.dp)
                                                .background(appleBlue().copy(alpha = 0.12f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${idx + 1}", fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold, color = appleBlue())
                                        }
                                        Text(
                                            q.take(40) + if (q.length > 40) "…" else "",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                },
                                trailingIcon = {
                                    // Delete icon for all except index 0 (protected default)
                                    if (idx > 0) {
                                        IconButton(
                                            onClick = { viewModel.deleteCustomQuestion(q) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, "Delete",
                                                modifier = Modifier.size(13.dp),
                                                tint = appleRed().copy(alpha = 0.7f))
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setPresetQuestion(q)
                                    showQuestionsMenu = false
                                }
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.Bookmark, null,
                                        modifier = Modifier.size(14.dp), tint = appleBlue())
                                    Text("Save current question",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = appleBlue())
                                }
                            },
                            onClick = {
                                showQuestionsMenu = false
                                if (uiState.question.isNotBlank()) showSaveDialog = true
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppleSpacing.xs))
            HigGroupedCard {
                TextField(
                    value = uiState.question,
                    onValueChange = viewModel::updateQuestion,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask anything about this chart...",
                        color = appleSecondaryLabel()) },
                    minLines = 3, maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.runAnalysis() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = appleSecondaryGroupedBackground(),
                        unfocusedContainerColor = appleSecondaryGroupedBackground(),
                        focusedIndicatorColor = appleBlue(),
                        unfocusedIndicatorColor = appleSeparator(),
                        focusedTextColor = appleLabel(), unfocusedTextColor = appleLabel(),
                        cursorColor = appleBlue()
                    ),
                    trailingIcon = {
                        if (uiState.question.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuestion("") },
                                modifier = Modifier.size(AppleSpacing.xxxl)) {
                                Box(Modifier.size(18.dp)
                                    .background(appleGray().copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Close, "Clear",
                                        modifier = Modifier.size(10.dp),
                                        tint = appleSecondaryGroupedBackground())
                                }
                            }
                        }
                    }
                )
            }
        }

        // ── Raw data toggle (for AI tools that cannot read images) ─────────
        item {
            HigGroupedCard {
                HigSwitchRow(
                    title = "Send raw chart data",
                    subtitle = "Also send OHLCV data as text for AI tools without image support",
                    checked = uiState.sendRawData,
                    onToggle = { viewModel.toggleRawData() }
                )
            }
        }

        // ── Analyze Button — mint green, left/right layout ─────────────────
        item {
            val provider = uiState.activeProvider
            val isReady  = uiState.isActiveProviderReady
            val modelName = uiState.activeModel?.displayName ?: provider?.name ?: "AI"
            val mintGreen = Color(0xFF3DAC78)
            val buttonEnabled = !uiState.isAnalyzing &&
                (uiState.chartBitmap != null || selectedTicker.isNotBlank() || uiState.sendRawData)

            Column(modifier = Modifier.padding(horizontal = AppleSpacing.base),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = viewModel::runAnalysis,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = buttonEnabled,
                    shape = RoundedCornerShape(AppleShapes.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mintGreen, contentColor = Color.White,
                        disabledContainerColor = appleGray().copy(alpha = 0.2f),
                        disabledContentColor = appleGray()
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    contentPadding = PaddingValues(horizontal = AppleSpacing.base)
                ) {
                    if (uiState.isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp),
                            color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    // Left: large primary label
                    Text(
                        if (uiState.isAnalyzing) "Analyzing…" else "Analyze Chart",
                        fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    // Vertical divider
                    if (provider != null && isReady && !uiState.isAnalyzing) {
                        Box(Modifier.width(0.5.dp).height(24.dp)
                            .background(Color.White.copy(alpha = 0.4f)))
                        Spacer(Modifier.width(AppleSpacing.sm))
                        // Right: small provider info
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${provider.emoji} $modelName",
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f))
                            if (!isReady) {
                                Text("Setup needed",
                                    fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        // ── Error ──────────────────────────────────────────────────────────
        uiState.error?.let { error ->
            item {
                // HIG: Error states use systemRed tinted container
                HigGroupedCard(modifier = Modifier
                    .background(appleRed().copy(alpha = 0.06f))) {
                    Row(
                        modifier = Modifier.padding(AppleSpacing.base),
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null,
                            tint = appleRed(), modifier = Modifier.size(20.dp))
                        Text(error,
                            style = MaterialTheme.typography.headlineSmall,
                            color = appleRed(),
                            modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = viewModel::clearError,
                            modifier = Modifier.size(AppleSpacing.xxxl)
                        ) {
                            Icon(Icons.Filled.Close, "Dismiss",
                                modifier = Modifier.size(16.dp),
                                tint = appleSecondaryLabel())
                        }
                    }
                }
            }
        }

        // ── Analysis Result ────────────────────────────────────────────────
        if (uiState.analysisText.isNotBlank()) {
            item {
                HigSectionHeader("AI Analysis")
                Spacer(Modifier.height(AppleSpacing.xs))
                HigAnalysisResultCard(uiState.analysisText)
            }
        }

        item { Spacer(Modifier.height(AppleSpacing.xl)) }
    }
}

// ─── Ticker Selector Dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TickerSelectorDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val popularTickers = listOf(
        "BTCUSDT","ETHUSDT","BNBUSDT","SOLUSDT","XRPUSDT",
        "ADAUSDT","DOGEUSDT","AVAXUSDT","LINKUSDT","MATICUSDT",
        "DOTUSDT","LTCUSDT","UNIUSDT","ATOMUSDT","ETCUSDT",
        "FILUSDT","NEARUSDT","APTUSDT","ARBUSDT","OPUSDT"
    )
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) popularTickers
    else popularTickers.filter { it.contains(query.uppercase()) } +
         if (query.length >= 2) listOf("${query.uppercase()}USDT") else emptyList()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AppleShapes.lg),
            color = appleSecondaryGroupedBackground()
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(AppleSpacing.base),
                verticalArrangement = Arrangement.spacedBy(AppleSpacing.md)) {
                Text("Select Ticker",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = appleLabel())
                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search BTC, ETH, SOL…", color = appleSecondaryLabel()) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = appleSecondaryLabel()) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" },
                            modifier = Modifier.size(AppleSpacing.xxxl)) {
                            Icon(Icons.Filled.Cancel, "Clear", tint = appleSecondaryLabel())
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppleShapes.sm),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appleBlue(),
                        unfocusedBorderColor = appleSeparator(),
                        focusedTextColor = appleLabel(), unfocusedTextColor = appleLabel(),
                        cursorColor = appleBlue()
                    )
                )
                // Ticker grid
                LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(filtered.distinct()) { sym ->
                        Surface(
                            onClick = { onSelect(sym) },
                            color = appleSecondaryGroupedBackground()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm + 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(sym.removeSuffix("USDT"),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold, color = appleLabel())
                                    Text("/USDT", style = MaterialTheme.typography.labelSmall,
                                        color = appleSecondaryLabel())
                                }
                                Icon(Icons.Filled.ChevronRight, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = appleGray().copy(alpha = 0.4f))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(0.5.dp)
                            .padding(start = AppleSpacing.base).background(appleSeparator()))
                    }
                }
                // Cancel button
                OutlinedButton(onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(AppleSpacing.xxxl),
                    shape = RoundedCornerShape(AppleShapes.sm),
                    border = BorderStroke(1.dp, appleSeparator())) {
                    Text("Cancel", color = appleBlue(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── Analyze Chart WebView (embedded live chart for Analyze tab) ──────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AnalyzeChartWebView(
    symbol: String,
    viewModel: ChartAnalyzerViewModel,
    modifier: Modifier = Modifier
) {
    // Embedded live chart for the Analyze tab (read-only, capture-for-analysis)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(AppleShapes.md),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0d1117)),
        border = BorderStroke(0.5.dp, appleSeparator())
    ) {
        Column {
            // Header: symbol + capture button
            Row(modifier = Modifier.fillMaxWidth()
                .background(appleSecondaryGroupedBackground())
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CandlestickChart, null,
                        tint = appleBlue(), modifier = Modifier.size(16.dp))
                    Text(symbol, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold, color = appleLabel())
                    Text("• Live", style = MaterialTheme.typography.labelSmall,
                        color = appleGreen())
                }
                // Capture button
                TextButton(onClick = { viewModel.captureChartForAnalysis(symbol) },
                    contentPadding = PaddingValues(horizontal = AppleSpacing.sm)) {
                    Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Capture for Analysis", style = MaterialTheme.typography.titleMedium)
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))

            // Embedded TradingView lightweight chart
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        loadUrl("file:///android_asset/chart.html")
                    }
                },
                update = { wv ->
                    // Push the symbol to the chart once it loads
                    wv.evaluateJavascript(
                        "if(window.setChartData){ /* chart ready */ }", null
                    )
                }
            )
        }
    }
}

// ─── HIG Upload Zone ──────────────────────────────────────────────────────────
// HIG: Dashed border indicates a drop target; centered icon + text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HigUploadZone(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(AppleShapes.md),
        color = appleBlue().copy(alpha = 0.05f),
        border = BorderStroke(1.5.dp, appleBlue().copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // HIG: SF Symbol equivalent — photo.badge.plus
            Icon(
                Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = appleBlue().copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(AppleSpacing.sm))
            Text(
                "Tap to upload chart image",
                style = MaterialTheme.typography.titleLarge,
                color = appleBlue()
            )
            Text(
                "PNG, JPG, WebP",
                style = MaterialTheme.typography.labelSmall,
                color = appleSecondaryLabel()
            )
        }
    }
}

// ─── HIG Chart Image Card ─────────────────────────────────────────────────────

@Composable
fun HigChartImageCard(bitmap: Bitmap, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(AppleShapes.md),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = appleSecondaryGroupedBackground()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Chart image",
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                contentScale = ContentScale.FillWidth
            )
            // HIG: Close button — systemGray filled circle, top-right
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppleSpacing.sm)
                    .size(AppleSpacing.xxxl)  // 44dp touch target
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(appleSecondaryGroupedBackground().copy(alpha = 0.92f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Remove",
                        modifier = Modifier.size(14.dp),
                        tint = appleLabel())
                }
            }
        }
    }
}

// ─── HIG Analysis Result Card ─────────────────────────────────────────────────
// HIG: Inset grouped card, monospaced result, copy action in secondary label color

@Composable
fun HigAnalysisResultCard(text: String) {
    val clipboard = LocalClipboardManager.current
    HigGroupedCard(modifier = Modifier.padding(horizontal = AppleSpacing.base)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null,
                        tint = appleBlue(), modifier = Modifier.size(16.dp))
                    Text("AI Analysis",
                        style = MaterialTheme.typography.headlineMedium)
                }
                // HIG: Action buttons are text buttons in systemBlue
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(text)) },
                    contentPadding = PaddingValues(horizontal = AppleSpacing.sm)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.titleMedium)
                }
            }
            // HIG: Separator — hairline, inset
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .padding(start = AppleSpacing.base)
                .background(appleSeparator()))

            Text(
                text,
                modifier = Modifier.padding(AppleSpacing.base),
                style = MaterialTheme.typography.headlineSmall,
                lineHeight = 24.sp,
                color = appleLabel()
            )
        }
    }
}

// ─── Frameworks Tab ───────────────────────────────────────────────────────────
// HIG: Inset grouped list by category section; iOS-style toggle rows

@Composable
fun FrameworksTab(uiState: AnalysisUiState, viewModel: ChartAnalyzerViewModel) {
    val grouped = TechnicalFrameworks.all.groupBy { it.category }
    // Expanded formula state — resets when leaving tab (key = true means each composition fresh)
    val expandedFormulas = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appleGroupedBackground())
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${uiState.activeFrameworkIds.size} of ${TechnicalFrameworks.all.size} active",
                style = MaterialTheme.typography.titleMedium,
                color = appleSecondaryLabel()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.base)) {
                TextButton(onClick = viewModel::selectAllFrameworks,
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Select All", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = viewModel::clearAllFrameworks,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = appleRed())) {
                    Text("Clear", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))

        LazyColumn(
            contentPadding = PaddingValues(vertical = AppleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppleSpacing.xl)
        ) {
            grouped.forEach { (category, frameworks) ->
                item {
                    HigCategorySection(
                        category = category,
                        frameworks = frameworks,
                        activeIds = uiState.activeFrameworkIds,
                        expandedFormulas = expandedFormulas,
                        onToggle = viewModel::toggleFramework
                    )
                }
            }
            item { Spacer(Modifier.height(AppleSpacing.xl)) }
        }
    }
}

@Composable
fun HigCategorySection(
    category: FrameworkCategory,
    frameworks: List<TechnicalFramework>,
    activeIds: Set<String>,
    expandedFormulas: MutableMap<String, Boolean>,
    onToggle: (String) -> Unit
) {
    val categoryColor = when (category) {
        FrameworkCategory.VOLUME      -> appleBlue()
        FrameworkCategory.TREND       -> appleGreen()
        FrameworkCategory.MOMENTUM    -> appleOrange()
        FrameworkCategory.PATTERN     -> applePurple()
        FrameworkCategory.STRUCTURE   -> appleTeal()
        FrameworkCategory.VOLATILITY  -> appleYellow()
        FrameworkCategory.SMART_MONEY -> appleRed()
        FrameworkCategory.ORDER_FLOW  -> appleBlue()
        FrameworkCategory.MACRO_CYCLE -> Color(0xFF64D2FF)
    }

    Column {
        HigSectionHeader(category.label)
        Spacer(Modifier.height(AppleSpacing.xs))
        HigGroupedCard {
            frameworks.forEachIndexed { i, framework ->
                val isActive = framework.id in activeIds
                val isFormulaExpanded = expandedFormulas[framework.id] == true

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppleSpacing.xxxl)
                            .padding(end = AppleSpacing.base),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: formula expand/collapse toggle arrow
                        IconButton(
                            onClick = {
                                expandedFormulas[framework.id] = !isFormulaExpanded
                            },
                            modifier = Modifier.size(AppleSpacing.xxxl)
                        ) {
                            Icon(
                                if (isFormulaExpanded) Icons.Filled.KeyboardArrowUp
                                else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (isFormulaExpanded) "Hide formula" else "Show formula",
                                tint = categoryColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        // Framework name + subtitle
                        Column(modifier = Modifier.weight(1f)) {
                            Text(framework.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = appleLabel())
                            Text(framework.shortName + " — " + framework.category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = appleSecondaryLabel())
                        }
                        // Right: on/off switch
                        Switch(
                            checked = isActive,
                            onCheckedChange = { onToggle(framework.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = appleGreen(),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = appleGray().copy(alpha = 0.25f),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }

                    // Collapsible formula section — hidden by default
                    AnimatedVisibility(
                        visible = isFormulaExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = AppleSpacing.base,
                                     end = AppleSpacing.base,
                                     bottom = AppleSpacing.md)) {
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))
                            Spacer(Modifier.height(AppleSpacing.sm))
                            // Formula code block
                            Surface(
                                shape = RoundedCornerShape(AppleShapes.sm),
                                color = appleGray().copy(alpha = 0.09f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    framework.formula,
                                    modifier = Modifier.padding(AppleSpacing.md),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 17.sp
                                    ),
                                    color = appleLabel(),
                                    fontSize = 11.sp
                                )
                            }
                            // Description
                            if (framework.description.isNotBlank()) {
                                Spacer(Modifier.height(AppleSpacing.sm))
                                Text(framework.description,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = appleSecondaryLabel(), lineHeight = 19.sp)
                            }
                        }
                    }
                }

                if (i < frameworks.size - 1) {
                    HigDivider(startInset = AppleSpacing.xxxl)
                }
            }
        }
    }
}

// ─── Formulas Tab ─────────────────────────────────────────────────────────────
// HIG: Search bar at top (iOS-style), inset grouped expandable rows

@Composable
fun FormulasTab(uiState: AnalysisUiState) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = TechnicalFrameworks.all.filter { fw ->
        fw.id in uiState.activeFrameworkIds && (
            searchQuery.isEmpty() ||
            fw.name.contains(searchQuery, ignoreCase = true) ||
            fw.formula.contains(searchQuery, ignoreCase = true)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // HIG: Search bar — rounded, systemGray fill, no border
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
            placeholder = { Text("Search formulas…", color = appleSecondaryLabel()) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = appleSecondaryLabel()) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" },
                        modifier = Modifier.size(AppleSpacing.xxxl)) {
                        Icon(Icons.Filled.Cancel, "Clear", tint = appleSecondaryLabel())
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(AppleShapes.sm),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = appleGray().copy(alpha = 0.12f),
                unfocusedContainerColor = appleGray().copy(alpha = 0.12f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedTextColor = appleLabel(),
                unfocusedTextColor = appleLabel(),
                cursorColor = appleBlue()
            )
        )

        LazyColumn(
            contentPadding = PaddingValues(vertical = AppleSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AppleSpacing.lg)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(AppleSpacing.xxxl),
                        contentAlignment = Alignment.Center) {
                        Text("No formulas match",
                            style = MaterialTheme.typography.headlineSmall,
                            color = appleSecondaryLabel())
                    }
                }
            } else {
                // Group by category
                val byCategory = filtered.groupBy { it.category }
                byCategory.forEach { (category, fws) ->
                    item {
                        HigSectionHeader(category.label)
                        Spacer(Modifier.height(AppleSpacing.xs))
                        HigGroupedCard {
                            fws.forEachIndexed { i, fw ->
                                HigFormulaRow(fw)
                                if (i < fws.size - 1) HigDivider()
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(AppleSpacing.xl)) }
        }
    }
}

@Composable
fun HigFormulaRow(framework: TechnicalFramework) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppleSpacing.xxxl)
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(framework.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = appleLabel())
                Text(framework.shortName,
                    style = MaterialTheme.typography.titleMedium,
                    color = appleBlue())
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ChevronRight,
                null,
                modifier = Modifier.size(20.dp),
                tint = appleGray().copy(alpha = 0.5f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(
                PaddingValues(horizontal = AppleSpacing.base, vertical = AppleSpacing.md))) {
                HigDivider()
                Spacer(Modifier.height(AppleSpacing.sm))
                // HIG: Code block — secondary fill background, monospace
                Surface(
                    shape = RoundedCornerShape(AppleShapes.sm),
                    color = appleGray().copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        framework.formula,
                        modifier = Modifier.padding(AppleSpacing.md),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        color = appleLabel()
                    )
                }
                Spacer(Modifier.height(AppleSpacing.sm))
                Text(framework.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = appleSecondaryLabel(),
                    lineHeight = 20.sp)
            }
        }
    }
}

// ─── History Tab ──────────────────────────────────────────────────────────────
// HIG: Inset grouped list, expandable history rows

@Composable
fun HistoryTab(uiState: AnalysisUiState) {
    if (uiState.analysisHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppleSpacing.sm)) {
                Icon(Icons.Outlined.History, null, modifier = Modifier.size(52.dp),
                    tint = appleGray().copy(alpha = 0.35f))
                Text("No History", style = MaterialTheme.typography.headlineMedium,
                    color = appleLabel())
                Text("Analyze a chart to see results here",
                    style = MaterialTheme.typography.titleMedium,
                    color = appleSecondaryLabel())
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = AppleSpacing.md),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            HigSectionHeader("${uiState.analysisHistory.size} analyses")
            Spacer(Modifier.height(AppleSpacing.xs))
        }
        item {
            HigGroupedCard {
                uiState.analysisHistory.forEachIndexed { i, entry ->
                    HigHistoryRow(entry)
                    if (i < uiState.analysisHistory.size - 1) HigDivider()
                }
            }
        }
        item { Spacer(Modifier.height(AppleSpacing.xl)) }
    }
}

@Composable
fun HigHistoryRow(entry: HistoryEntry) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.question,
                    style = MaterialTheme.typography.headlineSmall,
                    color = appleLabel(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm)) {
                    Text(fmt.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = appleSecondaryLabel())
                    Text("•", style = MaterialTheme.typography.labelSmall,
                        color = appleSecondaryLabel())
                    Text("${entry.frameworkCount} frameworks",
                        style = MaterialTheme.typography.labelSmall,
                        color = appleBlue())
                    Text("•", style = MaterialTheme.typography.labelSmall,
                        color = appleSecondaryLabel())
                    Text(entry.modelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = applePurple())
                }
            }
            Spacer(Modifier.width(AppleSpacing.sm))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ChevronRight,
                null, modifier = Modifier.size(20.dp),
                tint = appleGray().copy(alpha = 0.5f)
            )
        }

        AnimatedVisibility(visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(
                start = AppleSpacing.base, end = AppleSpacing.base, bottom = AppleSpacing.md)) {
                HigDivider()
                Spacer(Modifier.height(AppleSpacing.sm))
                Text(entry.result,
                    style = MaterialTheme.typography.titleMedium,
                    color = appleLabel(),
                    lineHeight = 20.sp)
                Spacer(Modifier.height(AppleSpacing.sm))
                // HIG: Copy button in systemBlue text style, right-aligned
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(entry.result)) },
                        contentPadding = PaddingValues(horizontal = AppleSpacing.xs)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// ─── API Key Sheet ────────────────────────────────────────────────────────────
// HIG: Use bottom sheet / alert — not a full-screen modal for secondary flows
// HIG: Destructive action (clear key) in systemRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySheet(
    currentKey: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Anthropic API Key",
                style = MaterialTheme.typography.headlineMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppleSpacing.md)) {
                Text(
                    "Enter your Anthropic API key to enable AI chart analysis. " +
                    "Get one at console.anthropic.com.",
                    style = MaterialTheme.typography.titleMedium,
                    color = appleSecondaryLabel()
                )
                // HIG: Rounded text field with show/hide button
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("sk-ant-…", color = appleSecondaryLabel()) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey },
                            modifier = Modifier.size(AppleSpacing.xxxl)) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff
                                else Icons.Outlined.Visibility,
                                if (showKey) "Hide" else "Show",
                                tint = appleSecondaryLabel()
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppleShapes.sm),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appleBlue(),
                        unfocusedBorderColor = appleSeparator(),
                        focusedTextColor = appleLabel(),
                        unfocusedTextColor = appleLabel(),
                        cursorColor = appleBlue()
                    )
                )
            }
        },
        // HIG: Primary action (Save) in systemBlue, Cancel is plain text
        confirmButton = {
            Button(
                onClick = { onConfirm(keyInput.trim()) },
                enabled = keyInput.isNotBlank(),
                shape = RoundedCornerShape(AppleShapes.sm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appleBlue(),
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = appleBlue())
            }
        },
        containerColor = appleSecondaryGroupedBackground(),
        shape = RoundedCornerShape(AppleShapes.lg)
    )
}
