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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chartanalyzer.app.models.*
import com.chartanalyzer.app.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ─── Formatters ───────────────────────────────────────────────────────────────

private fun formatPrice(price: Double): String = when {
    price >= 1000 -> NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2; minimumFractionDigits = 2 }.format(price)
    price >= 1    -> "%.4f".format(price)
    price >= 0.01 -> "%.6f".format(price)
    else          -> "%.8f".format(price)
}

private fun formatVolume(v: Double): String = when {
    v >= 1_000_000_000 -> "${"%.2f".format(v / 1_000_000_000)}B"
    v >= 1_000_000     -> "${"%.2f".format(v / 1_000_000)}M"
    v >= 1_000         -> "${"%.2f".format(v / 1_000)}K"
    else               -> "%.2f".format(v)
}

private fun formatPct(pct: Double): String = "${"%.2f".format(pct)}%"

// HIG: systemGreen for positive, systemRed for negative — semantic color use
@Composable
private fun pctColorHig(pct: Double): Color = if (pct >= 0) appleGreen() else appleRed()

// ─── Connection Status Badge — HIG: systemGreen for live, systemRed for error ──

@Composable
fun ConnectionStatusBadge(status: StreamStatus, modifier: Modifier = Modifier) {
    val (color, label) = when (status.state) {
        ConnectionState.CONNECTED    -> appleGreen() to "Live"
        ConnectionState.CONNECTING   -> appleOrange() to "Connecting"
        ConnectionState.RECONNECTING -> appleOrange() to "Reconnecting"
        ConnectionState.ERROR        -> appleRed() to "Error"
        ConnectionState.DISCONNECTED -> appleGray() to "Offline"
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // HIG: Status dot indicator — 7pt circle
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

// ─── ROOT: Binance Market Tab ─────────────────────────────────────────────────

enum class MarketScreen { WATCHLIST, TV_CHART, DETAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinanceMarketTab() {
    val viewModel: BinanceViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    var screen by remember { mutableStateOf(MarketScreen.WATCHLIST) }
    var chartSymbol by remember { mutableStateOf("BTCUSDT") }

    when (screen) {
        MarketScreen.WATCHLIST -> {
            WatchlistScreen(
                state = state,
                viewModel = viewModel,
                onSelectSymbol = { sym ->
                    chartSymbol = sym
                    viewModel.selectSymbol(sym)
                    screen = MarketScreen.TV_CHART
                }
            )
        }
        MarketScreen.TV_CHART -> {
            key(chartSymbol) {   // forces full recomposition when symbol changes
                TvChartScreen(
                    initialSymbol = chartSymbol,
                    onBack = { screen = MarketScreen.WATCHLIST }
                )
            }
        }
        MarketScreen.DETAIL -> {
            SymbolDetailScreen(
                state = state,
                viewModel = viewModel,
                onBack = { screen = MarketScreen.WATCHLIST }
            )
        }
    }
}

// ─── Watchlist Screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    state: BinanceUiState,
    viewModel: BinanceViewModel,
    onSelectSymbol: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var changePeriod  by remember { mutableStateOf("24h") }  // 1h, 24h, 7d, 30d
    var sortBy        by remember { mutableStateOf("rank") }  // rank, price, change
    var showSortMenu  by remember { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }

    val sortedWatchlist = remember(state.watchlist, state.tickerMap, sortBy, changePeriod) {
        when (sortBy) {
            "price"  -> state.watchlist.sortedByDescending {
                state.tickerMap[it.symbol]?.price ?: 0.0 }
            "change" -> state.watchlist.sortedByDescending {
                state.tickerMap[it.symbol]?.priceChangePct ?: 0.0 }
            else     -> state.watchlist   // rank = default order
        }
    }

    if (showAddDialog) {
        AddSymbolDialog(
            searchQuery    = state.searchQuery,
            searchResults  = state.searchResults,
            isSearching    = state.isSearching,
            onQueryChange  = viewModel::updateSearchQuery,
            onSelectSymbol = { sym ->
                viewModel.addToWatchlist(sym)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false; viewModel.clearSearch() }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(appleGroupedBackground())) {
        // Sub-header with status, sort, period, add
        Row(modifier = Modifier.fillMaxWidth()
            .background(appleSecondaryGroupedBackground())
            .padding(horizontal = AppleSpacing.sm, vertical = AppleSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {

            ConnectionStatusBadge(state.streamStatus)
            Spacer(Modifier.weight(1f))

            // % Change period selector
            Box {
                TextButton(onClick = { showPeriodMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("$changePeriod %",
                        style = MaterialTheme.typography.titleMedium,
                        color = appleBlue(), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.ArrowDropDown, null,
                        modifier = Modifier.size(16.dp), tint = appleBlue())
                }
                DropdownMenu(expanded = showPeriodMenu,
                    onDismissRequest = { showPeriodMenu = false },
                    modifier = Modifier.background(appleSecondaryGroupedBackground())) {
                    listOf("1h", "24h", "7d", "30d").forEach { period ->
                        DropdownMenuItem(
                            text = { Text("$period change",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (changePeriod == period) appleBlue() else appleLabel()) },
                            onClick = { changePeriod = period; showPeriodMenu = false }
                        )
                    }
                }
            }

            // Sort button
            Box {
                TextButton(onClick = { showSortMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, null,
                        modifier = Modifier.size(14.dp), tint = appleSecondaryLabel())
                    Spacer(Modifier.width(3.dp))
                    Text(sortBy.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = appleSecondaryLabel())
                }
                DropdownMenu(expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(appleSecondaryGroupedBackground())) {
                    listOf("rank" to "By Rank", "price" to "By Price",
                           "change" to "By % Change").forEach { (id, label) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (sortBy == id) Icon(Icons.Filled.Check, null,
                                        modifier = Modifier.size(14.dp), tint = appleBlue())
                                    else Spacer(Modifier.width(14.dp))
                                    Text(label, style = MaterialTheme.typography.titleMedium,
                                        color = if (sortBy == id) appleBlue() else appleLabel())
                                }
                            },
                            onClick = { sortBy = id; showSortMenu = false }
                        )
                    }
                }
            }

            // Add button
            IconButton(onClick = { showAddDialog = true },
                modifier = Modifier.size(AppleSpacing.xxxl)) {
                Icon(Icons.Filled.Add, "Add symbol", tint = appleBlue(),
                    modifier = Modifier.size(22.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))

        // Error banner
        state.error?.let { err ->
            Surface(color = appleRed().copy(alpha = 0.08f),
                border = BorderStroke(0.5.dp, appleRed().copy(alpha = 0.25f))) {
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null,
                        tint = appleRed(), modifier = Modifier.size(16.dp))
                    Text(err, style = MaterialTheme.typography.titleMedium,
                        color = appleRed(), modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.refreshAllTickers() },
                        contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Retry", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (state.watchlist.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppleSpacing.sm)) {
                    Icon(Icons.AutoMirrored.Outlined.TrendingUp, null,
                        modifier = Modifier.size(48.dp), tint = appleGray().copy(alpha = 0.3f))
                    Text("No symbols in watchlist",
                        style = MaterialTheme.typography.headlineMedium, color = appleLabel())
                    TextButton(onClick = { showAddDialog = true }) { Text("Add symbols") }
                }
            }
        } else {
            LazyColumn {
                itemsIndexed(sortedWatchlist, key = { _, item -> item.symbol }) { index, item ->
                    val ticker = state.tickerMap[item.symbol]
                    WatchlistRow(
                        item    = item,
                        rank    = index + 1,
                        ticker  = ticker,
                        onClick = { onSelectSymbol(item.symbol) },
                        onRemove = { viewModel.removeFromWatchlist(item.symbol) }
                    )
                    HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistRow(
    item: WatchlistItem,
    rank: Int = 0,
    ticker: TickerData?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(onClick = onClick, color = appleSecondaryGroupedBackground()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppleSpacing.xxxl)
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank number
            if (rank > 0) {
                Text("#$rank",
                    style = MaterialTheme.typography.labelSmall,
                    color = appleSecondaryLabel(),
                    modifier = Modifier.width(28.dp))
            }

            // Coin emoji icon
            val coinEmoji = coinEmojiFor(item.displayName)
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(AppleShapes.sm))
                .background(appleGray().copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center) {
                Text(coinEmoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(AppleSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = appleLabel())
                Text(item.quoteCurrency,
                    style = MaterialTheme.typography.labelSmall,
                    color = appleSecondaryLabel())
            }
            // Price column
            Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
                if (ticker != null) {
                    Text(formatPrice(ticker.price),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.headlineSmall,
                        color = appleLabel())
                    Text("Vol: ${formatVolume(ticker.quoteVolume)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = appleSecondaryLabel())
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp, color = appleBlue())
                }
            }
            // Change % badge
            Box(modifier = Modifier.width(72.dp).padding(start = AppleSpacing.sm),
                contentAlignment = Alignment.CenterEnd) {
                if (ticker != null) {
                    val pctColor = pctColorHig(ticker.priceChangePct)
                    Surface(shape = RoundedCornerShape(AppleShapes.xs),
                        color = pctColor.copy(alpha = 0.12f)) {
                        Text(formatPct(ticker.priceChangePct),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = pctColor)
                    }
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(AppleSpacing.xxxl)) {
                Icon(Icons.Filled.Close, "Remove",
                    modifier = Modifier.size(14.dp),
                    tint = appleGray().copy(alpha = 0.4f))
            }
        }
    }
}

// Map coin name to emoji icon
private fun coinEmojiFor(name: String): String = when (name.uppercase()) {
    "BTC"   -> "₿"
    "ETH"   -> "🔷"
    "BNB"   -> "🟡"
    "SOL"   -> "◎"
    "XRP"   -> "💧"
    "ADA"   -> "🔵"
    "DOGE"  -> "🐶"
    "DOT"   -> "⚫"
    "AVAX"  -> "🔺"
    "MATIC","POL" -> "🟣"
    "LINK"  -> "🔗"
    "LTC"   -> "Ł"
    "ATOM"  -> "⚛️"
    "UNI"   -> "🦄"
    "NEAR"  -> "🌐"
    "SHIB"  -> "🐕"
    "TRX"   -> "🔴"
    "ETC"   -> "🟩"
    "ARB"   -> "🔵"
    "OP"    -> "🔴"
    "APT"   -> "🔷"
    "FIL"   -> "📁"
    else    -> "🪙"
}

// ─── Symbol Detail Screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolDetailScreen(
    state: BinanceUiState,
    viewModel: BinanceViewModel,
    onBack: () -> Unit
) {
    val ticker = state.tickerMap[state.selectedSymbol]
    val cs = MaterialTheme.colorScheme
    val tabs = listOf("Chart", "Order Book", "Trades", "Historical")

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.selectedSymbol.removeSuffix("USDT"),
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("/USDT", style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant)
                        ConnectionStatusBadge(state.streamStatus)
                    }
                    if (ticker != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(formatPrice(ticker.price),
                                fontWeight = FontWeight.SemiBold,
                                color = pctColorHig(ticker.priceChangePct), fontSize = 15.sp)
                            Text(formatPct(ticker.priceChangePct),
                                fontSize = 12.sp,
                                color = pctColorHig(ticker.priceChangePct))
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = viewModel::refreshAll) {
                    Icon(Icons.Filled.Refresh, "Refresh")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            windowInsets = WindowInsets.statusBars
        )

        // 24h stats strip
        if (ticker != null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { StatChip("24H High", formatPrice(ticker.highPrice)) }
                item { StatChip("24H Low", formatPrice(ticker.lowPrice)) }
                item { StatChip("Volume", formatVolume(ticker.volume)) }
                item { StatChip("Quote Vol", formatVolume(ticker.quoteVolume)) }
                item { StatChip("Open", formatPrice(ticker.openPrice)) }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }

        // Tab row
        ScrollableTabRow(
            selectedTabIndex = state.selectedDetailTab,
            edgePadding = 0.dp,
            containerColor = cs.surface,
            contentColor = cs.primary,
            divider = { HorizontalDivider(thickness = 0.5.dp) }
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = state.selectedDetailTab == i,
                    onClick = { viewModel.setDetailTab(i) },
                    text = { Text(title, fontSize = 13.sp) }
                )
            }
        }

        // Tab content
        when (state.selectedDetailTab) {
            0 -> ChartTab(state, viewModel)
            1 -> OrderBookTab(state, viewModel)
            2 -> TradesTab(state)
            3 -> HistoricalTab(state, viewModel)
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}

// ─── Chart Tab (candles + interval selector) ──────────────────────────────────

@Composable
fun ChartTab(state: BinanceUiState, viewModel: BinanceViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Interval selector
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val intervalsToShow = if (state.availableIntervals.isNotEmpty())
                state.availableIntervals
            else CandleInterval.values().toList()

            items(intervalsToShow) { interval ->
                FilterChip(
                    selected = state.selectedInterval == interval,
                    onClick = { viewModel.selectInterval(interval) },
                    label = { Text(interval.label, fontSize = 12.sp) },
                    modifier = Modifier.height(30.dp)
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp)

        // OHLCV mini stats of latest candle
        val latestCandle = state.liveCandle ?: state.candles.lastOrNull()
        if (latestCandle != null) {
            CandleStatsRow(latestCandle, state.selectedInterval)
            HorizontalDivider(thickness = 0.5.dp)
        }

        if (state.isLoadingCandles) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading ${state.selectedInterval.displayName} candles...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (state.candles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No candle data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            CandleListView(
                candles = state.candles,
                liveCandle = state.liveCandle,
                symbol = state.selectedSymbol,
                interval = state.selectedInterval
            )
        }
    }
}

@Composable
fun CandleStatsRow(candle: Candle, @Suppress("UNUSED_PARAMETER") interval: CandleInterval) {
    val cs = MaterialTheme.colorScheme
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(fmt.format(Date(candle.openTime)),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OHLCLabel("O", formatPrice(candle.open), cs.onSurface)
            OHLCLabel("H", formatPrice(candle.high), cs.tertiary)
            OHLCLabel("L", formatPrice(candle.low), cs.error)
            OHLCLabel("C", formatPrice(candle.close),
                if (candle.isBullish) cs.tertiary else cs.error)
            OHLCLabel("V", formatVolume(candle.volume), cs.onSurfaceVariant)
        }
        if (!candle.isClosed) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = cs.primary.copy(alpha = 0.15f)
            ) {
                Text("LIVE", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = cs.primary, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
fun OHLCLabel(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CandleListView(
    candles: List<Candle>,
    liveCandle: Candle?,
    @Suppress("UNUSED_PARAMETER") symbol: String,
    interval: CandleInterval
) {
    val cs = MaterialTheme.colorScheme
    val allCandles = if (liveCandle != null) candles + liveCandle else candles
    val fmt = SimpleDateFormat("MMM d HH:mm", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary stats bar
        val closes = candles.map { it.close }
        if (closes.isNotEmpty()) {
            val minC = closes.min()
            val maxC = closes.max()
            val avgC = closes.average()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${candles.size} bars  •  ${interval.displayName}",
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text("Range: ${formatPrice(minC)} – ${formatPrice(maxC)}",
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text("Avg: ${formatPrice(avgC)}",
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }

        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Time", "Open", "High", "Low", "Close", "Volume").forEachIndexed { i, h ->
                Text(h, style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = if (i == 0) Modifier.width(90.dp) else Modifier.weight(1f),
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
            }
        }
        HorizontalDivider(thickness = 0.5.dp)

        LazyColumn {
            items(allCandles.reversed(), key = { it.openTime }) { candle ->
                val isLive = !candle.isClosed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isLive) cs.primary.copy(alpha = 0.05f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fmt.format(Date(candle.openTime)),
                        fontSize = 11.sp, color = cs.onSurfaceVariant,
                        modifier = Modifier.width(90.dp))
                    val rowColor = if (candle.isBullish) cs.tertiary else cs.error
                    listOf(candle.open, candle.high, candle.low, candle.close).forEach { v ->
                        Text(formatPrice(v), fontSize = 11.sp,
                            color = if (v == candle.close) rowColor else cs.onSurface,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    Text(formatVolume(candle.volume), fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                HorizontalDivider(thickness = 0.3.dp,
                    color = cs.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

// ─── Order Book Tab ───────────────────────────────────────────────────────────

@Composable
fun OrderBookTab(state: BinanceUiState, viewModel: BinanceViewModel) {
    val cs = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isLoadingOrderBook) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading order book...", color = cs.onSurfaceVariant)
                }
            }
            return
        }

        val ob = state.orderBook
        if (ob == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No order book data", color = cs.onSurfaceVariant)
                    Button(onClick = { viewModel.loadOrderBook() }) { Text("Load") }
                }
            }
            return
        }

        // Summary metrics
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ObMetric("Best Bid", formatPrice(ob.bestBid), cs.tertiary)
                ObMetric("Spread", formatPrice(ob.spread) + "\n${"%.4f".format(ob.spreadPct)}%", cs.onSurface)
                ObMetric("Best Ask", formatPrice(ob.bestAsk), cs.error)
            }
        }

        // Imbalance bar
        val imb = ob.bidAskImbalance
        val imbLabel = when {
            imb > 0.3  -> "Strong Buy Pressure"
            imb > 0.1  -> "Mild Buy Pressure"
            imb < -0.3 -> "Strong Sell Pressure"
            imb < -0.1 -> "Mild Sell Pressure"
            else       -> "Balanced"
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bid/Ask Imbalance", style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant)
                Text(imbLabel, style = MaterialTheme.typography.labelSmall,
                    color = if (imb >= 0) cs.tertiary else cs.error,
                    fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(4.dp))
            val bidFraction = ((1.0 + imb) / 2.0).coerceIn(0.0, 1.0).toFloat()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Box(Modifier.fillMaxHeight().weight(bidFraction).background(cs.tertiary))
                Box(Modifier.fillMaxHeight().weight(1f - bidFraction).background(cs.error))
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bids: ${formatVolume(ob.totalBidVolume)}", fontSize = 10.sp, color = cs.tertiary)
                Text("Asks: ${formatVolume(ob.totalAskVolume)}", fontSize = 10.sp, color = cs.error)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

        // Book columns header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Bid Qty", style = MaterialTheme.typography.labelSmall,
                color = cs.tertiary, modifier = Modifier.weight(1f))
            Text("Bid Price", style = MaterialTheme.typography.labelSmall,
                color = cs.tertiary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Spacer(Modifier.width(16.dp))
            Text("Ask Price", style = MaterialTheme.typography.labelSmall,
                color = cs.error, modifier = Modifier.weight(1f))
            Text("Ask Qty", style = MaterialTheme.typography.labelSmall,
                color = cs.error, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(thickness = 0.5.dp)

        val maxBid = ob.bids.maxOfOrNull { it.quantity } ?: 1.0
        val maxAsk = ob.asks.maxOfOrNull { it.quantity } ?: 1.0

        LazyColumn {
            val rows = maxOf(ob.bids.size, ob.asks.size)
            items(rows) { i ->
                val bid = ob.bids.getOrNull(i)
                val ask = ob.asks.getOrNull(i)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bid side
                    Box(modifier = Modifier.weight(2f)) {
                        if (bid != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((bid.quantity / maxBid).toFloat())
                                    .height(22.dp)
                                    .background(cs.tertiary.copy(alpha = 0.12f))
                                    .align(Alignment.CenterStart)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("%.4f".format(bid.quantity), fontSize = 11.sp, color = cs.tertiary)
                                Text(formatPrice(bid.price), fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium, color = cs.tertiary)
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    // Ask side
                    Box(modifier = Modifier.weight(2f)) {
                        if (ask != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((ask.quantity / maxAsk).toFloat())
                                    .height(22.dp)
                                    .background(cs.error.copy(alpha = 0.12f))
                                    .align(Alignment.CenterEnd)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(formatPrice(ask.price), fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium, color = cs.error)
                                Text("%.4f".format(ask.quantity), fontSize = 11.sp, color = cs.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ObMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = color,
            fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ─── Trades Tab ───────────────────────────────────────────────────────────────

@Composable
fun TradesTab(state: BinanceUiState) {
    val cs = MaterialTheme.colorScheme
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Time", style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, modifier = Modifier.width(72.dp))
            Text("Price", style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Amount", style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("Value", style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        HorizontalDivider(thickness = 0.5.dp)

        if (state.recentTrades.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Waiting for trades...", color = cs.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn {
                items(state.recentTrades, key = { it.tradeId }) { trade ->
                    val color = if (trade.isBullish) cs.tertiary else cs.error
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(fmt.format(Date(trade.timestamp)),
                            fontSize = 11.sp, color = cs.onSurfaceVariant,
                            modifier = Modifier.width(72.dp))
                        Text(formatPrice(trade.price), fontSize = 11.sp,
                            fontWeight = FontWeight.Medium, color = color,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text("%.4f".format(trade.quantity), fontSize = 11.sp, color = cs.onSurface,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(formatVolume(trade.value), fontSize = 11.sp, color = cs.onSurfaceVariant,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    HorizontalDivider(thickness = 0.3.dp,
                        color = cs.outlineVariant.copy(alpha = 0.25f))
                }
            }
        }
    }
}

// ─── Historical Data Tab ──────────────────────────────────────────────────────

@Composable
fun HistoricalTab(state: BinanceUiState, viewModel: BinanceViewModel) {
    val cs = MaterialTheme.colorScheme
    var selectedInterval by remember { mutableStateOf(CandleInterval.ONE_DAY) }
    var selectedLimit by remember { mutableStateOf(100) }
    val limitOptions = listOf(50, 100, 200, 500, 1000)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {

        Text("Historical Data", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)

        // Interval selector
        Text("Interval", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(
                CandleInterval.ONE_MINUTE, CandleInterval.FIVE_MINUTES,
                CandleInterval.FIFTEEN_MINUTES, CandleInterval.ONE_HOUR,
                CandleInterval.FOUR_HOURS, CandleInterval.ONE_DAY,
                CandleInterval.ONE_WEEK, CandleInterval.ONE_MONTH
            )) { interval ->
                FilterChip(
                    selected = selectedInterval == interval,
                    onClick = { selectedInterval = interval },
                    label = { Text(interval.label) }
                )
            }
        }

        // Limit selector
        Text("Number of bars", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            limitOptions.forEach { limit ->
                FilterChip(
                    selected = selectedLimit == limit,
                    onClick = { selectedLimit = limit },
                    label = { Text("$limit") }
                )
            }
        }

        Button(
            onClick = {
                viewModel.loadHistoricalData(
                    interval = selectedInterval,
                    limit = selectedLimit
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoadingHistorical
        ) {
            if (state.isLoadingHistorical) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp),
                    color = cs.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Loading...")
            } else {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Fetch $selectedLimit ${selectedInterval.displayName} Bars")
            }
        }

        if (state.historicalCandles.isNotEmpty()) {
            // Summary stats
            val closes = state.historicalCandles.map { it.close }
            val volumes = state.historicalCandles.map { it.volume }
            val bullishCount = state.historicalCandles.count { it.isBullish }
            val bearishCount = state.historicalCandles.size - bullishCount

            HorizontalDivider()
            Text("Summary — ${state.historicalCandles.size} bars",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("High", formatPrice(closes.max()), cs.tertiary, Modifier.weight(1f))
                SummaryCard("Low", formatPrice(closes.min()), cs.error, Modifier.weight(1f))
                SummaryCard("Avg", formatPrice(closes.average()), cs.onSurface, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Avg Vol", formatVolume(volumes.average()), cs.secondary, Modifier.weight(1f))
                SummaryCard("Bullish", "$bullishCount", cs.tertiary, Modifier.weight(1f))
                SummaryCard("Bearish", "$bearishCount", cs.error, Modifier.weight(1f))
            }

            Text("Recent Bars", style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant)

            val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(state.historicalCandles.takeLast(20).reversed()) { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(fmt.format(Date(c.openTime)), fontSize = 11.sp, color = cs.onSurfaceVariant,
                            modifier = Modifier.width(100.dp))
                        val color = if (c.isBullish) cs.tertiary else cs.error
                        Text(formatPrice(c.close), fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
                        Text(formatVolume(c.volume), fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text(if (c.isBullish) "▲" else "▼", fontSize = 11.sp, color = color)
                    }
                    HorizontalDivider(thickness = 0.3.dp, color = cs.outlineVariant.copy(alpha = 0.25f))
                }
            }
        }
    }
}

@Composable
fun SummaryCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

// ─── Add Symbol Dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSymbolDialog(
    searchQuery: String,
    searchResults: List<String>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectSymbol: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Resolve the symbol that will be added — always defined when query is non-empty
    val resolvedSymbol = remember(searchQuery, searchResults) {
        val q = searchQuery.trim().uppercase()
        when {
            q.isBlank() -> null
            searchResults.any { it.equals(q, ignoreCase = true) } ->
                searchResults.first { it.equals(q, ignoreCase = true) }
            q.endsWith("USDT") || q.endsWith("BTC") || q.endsWith("ETH") -> q
            else -> "${q}USDT"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AppleShapes.lg),
            color = appleSecondaryGroupedBackground(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppleSpacing.base),
                verticalArrangement = Arrangement.spacedBy(AppleSpacing.md)
            ) {
                // Title
                Text("Add to Watchlist",
                    style = MaterialTheme.typography.headlineMedium,
                    color = appleLabel())

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search e.g. BTC, ETH, SOL…",
                        color = appleSecondaryLabel()) },
                    leadingIcon = { Icon(Icons.Filled.Search, null,
                        tint = appleSecondaryLabel()) },
                    trailingIcon = {
                        when {
                            isSearching -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp, color = appleBlue())
                            searchQuery.isNotEmpty() ->
                                IconButton(onClick = { onQueryChange("") },
                                    modifier = Modifier.size(AppleSpacing.xxxl)) {
                                    Icon(Icons.Filled.Cancel, "Clear",
                                        tint = appleSecondaryLabel())
                                }
                        }
                    },
                    singleLine = true,
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

                // Symbol preview chip
                AnimatedVisibility(visible = resolvedSymbol != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Will add:", style = MaterialTheme.typography.labelSmall,
                            color = appleSecondaryLabel())
                        Surface(
                            shape = RoundedCornerShape(AppleShapes.full),
                            color = appleBlue().copy(alpha = 0.12f)
                        ) {
                            Text(resolvedSymbol ?: "",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = appleBlue(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Search results list (tap any row to add instantly)
                if (searchResults.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(AppleShapes.sm),
                        color = appleGroupedBackground(),
                        border = BorderStroke(0.5.dp, appleSeparator())
                    ) {
                        LazyColumn(modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)) {
                            items(searchResults) { sym ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectSymbol(sym) }
                                        .padding(
                                            horizontal = AppleSpacing.base,
                                            vertical = AppleSpacing.sm + 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(sym.removeSuffix("USDT"),
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = appleLabel(),
                                            fontWeight = FontWeight.Medium)
                                        Text("/USDT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = appleSecondaryLabel())
                                    }
                                    Icon(Icons.Outlined.AddCircle, "Add $sym",
                                        modifier = Modifier.size(20.dp), tint = appleBlue())
                                }
                                Box(Modifier.fillMaxWidth().height(0.5.dp)
                                    .padding(start = AppleSpacing.base)
                                    .background(appleSeparator()))
                            }
                        }
                    }
                } else if (searchQuery.isBlank()) {
                    Text("Type a coin name or symbol to search all USDT pairs on Binance.",
                        style = MaterialTheme.typography.titleMedium,
                        color = appleSecondaryLabel())
                } else if (!isSearching) {
                    Text("No results for \"$searchQuery\" — press Add to try it anyway.",
                        style = MaterialTheme.typography.titleMedium,
                        color = appleSecondaryLabel())
                }

                // ── Buttons — always visible, never clipped ────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm)
                ) {
                    // Cancel — secondary style
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(AppleSpacing.xxxl),
                        shape = RoundedCornerShape(AppleShapes.sm),
                        border = BorderStroke(1.dp, appleSeparator())
                    ) {
                        Text("Cancel", color = appleBlue(), fontWeight = FontWeight.SemiBold)
                    }
                    // Add — primary systemBlue button, enabled when query non-empty
                    Button(
                        onClick = {
                            val sym = resolvedSymbol ?: return@Button
                            onSelectSymbol(sym)
                        },
                        enabled = resolvedSymbol != null,
                        modifier = Modifier.weight(1f).height(AppleSpacing.xxxl),
                        shape = RoundedCornerShape(AppleShapes.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appleBlue(),
                            contentColor = Color.White,
                            disabledContainerColor = appleGray().copy(alpha = 0.15f),
                            disabledContentColor = appleGray()
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
