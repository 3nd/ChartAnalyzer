package com.chartanalyzer.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chartanalyzer.app.api.BinanceRestService
import com.chartanalyzer.app.api.BinanceWebSocketService
import com.chartanalyzer.app.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ─── UI State ────────────────────────────────────────────────────────────────

data class BinanceUiState(
    // Watchlist
    val watchlist: List<WatchlistItem> = PopularSymbols.defaults,
    val tickerMap: Map<String, TickerData> = emptyMap(),

    // Selected symbol detail
    val selectedSymbol: String = "BTCUSDT",
    val selectedInterval: CandleInterval = CandleInterval.ONE_HOUR,

    // Order book
    val orderBook: OrderBookData? = null,
    val isLoadingOrderBook: Boolean = false,

    // Candles
    val candles: List<Candle> = emptyList(),
    val liveCandle: Candle? = null,
    val isLoadingCandles: Boolean = false,

    // Historical
    val historicalCandles: List<Candle> = emptyList(),
    val isLoadingHistorical: Boolean = false,

    // Available intervals
    val availableIntervals: List<CandleInterval> = emptyList(),
    val isLoadingIntervals: Boolean = false,

    // Connection
    val streamStatus: StreamStatus = StreamStatus(),

    // Symbol search
    val searchQuery: String = "",
    val searchResults: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val allUsdtSymbols: List<String> = emptyList(),

    // Recent trades
    val recentTrades: List<TradeData> = emptyList(),

    // Error
    val error: String? = null,

    // Tabs
    val selectedDetailTab: Int = 0   // 0=chart, 1=orderbook, 2=trades
)

class BinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val wsService = BinanceWebSocketService()
    private val restService = BinanceRestService()

    private val _uiState = MutableStateFlow(BinanceUiState())
    val uiState: StateFlow<BinanceUiState> = _uiState.asStateFlow()

    private var tradeCollectorJob: Job? = null
    private var candleCollectorJob: Job? = null

    init {
        // Subscribe watchlist tickers via WebSocket
        val symbols = PopularSymbols.allSymbols
        wsService.subscribeToTickers(symbols)

        // Collect stream status
        viewModelScope.launch {
            wsService.streamStatus.collect { status ->
                _uiState.value = _uiState.value.copy(streamStatus = status)
            }
        }

        // Collect ticker updates → update tickerMap
        viewModelScope.launch {
            wsService.tickerUpdates.collect { ticker ->
                val newMap = _uiState.value.tickerMap.toMutableMap()
                newMap[ticker.symbol] = ticker
                _uiState.value = _uiState.value.copy(tickerMap = newMap, error = null)
            }
        }

        // Collect order book updates
        viewModelScope.launch {
            wsService.orderBookUpdates.collect { ob ->
                _uiState.value = _uiState.value.copy(orderBook = ob, isLoadingOrderBook = false)
            }
        }

        // ── Seed tickerMap immediately via REST so prices show before WS arrives ─
        viewModelScope.launch {
            restService.getMultipleTickers(PopularSymbols.allSymbols)
                .onSuccess { tickers ->
                    val seedMap = tickers.associateBy { it.symbol }
                    // Merge: WS data (if any) takes precedence over REST seed
                    _uiState.value = _uiState.value.copy(
                        tickerMap = seedMap + _uiState.value.tickerMap,
                        error = null
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = "Prices unavailable — tap Retry"
                    )
                    Log.w("BinanceVM", "REST seed failed: ${e.message}")
                }
        }

        // ── Periodic REST refresh every 15s as WS fallback ───────────────────
        // Ensures prices update even if WebSocket connection is slow/blocked
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val syms = _uiState.value.watchlist.map { it.symbol }
                if (syms.isNotEmpty()) {
                    restService.getMultipleTickers(syms)
                        .onSuccess { tickers ->
                            val newMap = _uiState.value.tickerMap.toMutableMap()
                            // Only update if WS hasn't provided a more recent value
                            tickers.forEach { t ->
                                if (!newMap.containsKey(t.symbol) || newMap[t.symbol]?.price == 0.0) {
                                    newMap[t.symbol] = t
                                }
                            }
                            _uiState.value = _uiState.value.copy(tickerMap = newMap, error = null)
                        }
                }
            }
        }

        // Load initial data for default symbol
        loadSymbolData("BTCUSDT", CandleInterval.ONE_HOUR)
    }

    // ─── Watchlist management ─────────────────────────────────────────────────

    fun addToWatchlist(symbol: String) {
        val sym = symbol.uppercase().let {
            if (it.endsWith("USDT") || it.endsWith("BTC") || it.endsWith("ETH")) it
            else "${it}USDT"
        }
        val current = _uiState.value.watchlist
        if (current.none { it.symbol == sym }) {
            val updated = current + WatchlistItem(sym)
            _uiState.value = _uiState.value.copy(watchlist = updated)
            wsService.addSymbol(sym)
            // Immediately fetch REST price for this new symbol
            viewModelScope.launch {
                restService.getTicker24h(sym).onSuccess { ticker ->
                    val newMap = _uiState.value.tickerMap.toMutableMap()
                    newMap[sym] = ticker
                    _uiState.value = _uiState.value.copy(tickerMap = newMap)
                }
            }
        }
    }

    fun refreshTickers() {
        val symbols = _uiState.value.watchlist.map { it.symbol }
        if (symbols.isEmpty()) return
        viewModelScope.launch {
            restService.getMultipleTickers(symbols)
                .onSuccess { tickers ->
                    val newMap = _uiState.value.tickerMap.toMutableMap()
                    tickers.forEach { newMap[it.symbol] = it }
                    _uiState.value = _uiState.value.copy(tickerMap = newMap, error = null)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = "Price refresh failed: ${e.message}"
                    )
                }
        }
    }

    fun refreshAllTickers() {
        viewModelScope.launch {
            val syms = _uiState.value.watchlist.map { it.symbol }
            if (syms.isNotEmpty()) {
                restService.getMultipleTickers(syms).onSuccess { tickers ->
                    val map = _uiState.value.tickerMap.toMutableMap()
                    tickers.forEach { map[it.symbol] = it }
                    _uiState.value = _uiState.value.copy(tickerMap = map, error = null)
                }
            }
        }
    }

    fun removeFromWatchlist(symbol: String) {
        val updated = _uiState.value.watchlist.filter { it.symbol != symbol }
        _uiState.value = _uiState.value.copy(watchlist = updated)
        wsService.removeSymbol(symbol)
    }

    // ─── Symbol selection ─────────────────────────────────────────────────────

    fun selectSymbol(symbol: String) {
        if (symbol == _uiState.value.selectedSymbol) return
        _uiState.value = _uiState.value.copy(
            selectedSymbol = symbol,
            orderBook = null,
            candles = emptyList(),
            liveCandle = null,
            recentTrades = emptyList(),
            error = null
        )
        loadSymbolData(symbol, _uiState.value.selectedInterval)
    }

    fun selectInterval(interval: CandleInterval) {
        if (interval == _uiState.value.selectedInterval) return
        _uiState.value = _uiState.value.copy(
            selectedInterval = interval,
            candles = emptyList(),
            liveCandle = null
        )
        val sym = _uiState.value.selectedSymbol
        loadCandles(sym, interval)
        wsService.subscribeToCandleStream(sym, interval)
        startCandleCollector()
    }

    // ─── Load all data for a symbol ───────────────────────────────────────────

    private fun loadSymbolData(symbol: String, interval: CandleInterval) {
        loadOrderBook(symbol)
        loadCandles(symbol, interval)
        loadAvailableIntervals(symbol)
        wsService.subscribeToOrderBook(symbol)
        wsService.subscribeToCandleStream(symbol, interval)
        wsService.subscribeToTrades(symbol)
        startCandleCollector()
        startTradeCollector()
    }

    // ─── Order book ───────────────────────────────────────────────────────────

    fun loadOrderBook(symbol: String = _uiState.value.selectedSymbol) {
        _uiState.value = _uiState.value.copy(isLoadingOrderBook = true, error = null)
        viewModelScope.launch {
            restService.getOrderBookSnapshot(symbol, limit = 20)
                .onSuccess { ob ->
                    _uiState.value = _uiState.value.copy(orderBook = ob, isLoadingOrderBook = false)
                    wsService.subscribeToOrderBook(symbol)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingOrderBook = false,
                        error = "Order book: ${e.message}"
                    )
                }
        }
    }

    // ─── Candles (current interval, REST snapshot) ────────────────────────────

    fun loadCandles(
        symbol: String = _uiState.value.selectedSymbol,
        interval: CandleInterval = _uiState.value.selectedInterval,
        limit: Int = 200
    ) {
        _uiState.value = _uiState.value.copy(isLoadingCandles = true, error = null)
        viewModelScope.launch {
            restService.getHistoricalCandles(
                HistoricalDataRequest(symbol, interval, limit)
            ).onSuccess { series ->
                _uiState.value = _uiState.value.copy(
                    candles = series.candles,
                    isLoadingCandles = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoadingCandles = false,
                    error = "Candles: ${e.message}"
                )
            }
        }
    }

    // ─── Historical data ──────────────────────────────────────────────────────

    fun loadHistoricalData(
        symbol: String = _uiState.value.selectedSymbol,
        interval: CandleInterval = _uiState.value.selectedInterval,
        limit: Int = 500,
        startTime: Long? = null,
        endTime: Long? = null
    ) {
        _uiState.value = _uiState.value.copy(isLoadingHistorical = true, error = null)
        viewModelScope.launch {
            restService.getHistoricalCandles(
                HistoricalDataRequest(symbol, interval, limit, startTime, endTime)
            ).onSuccess { series ->
                _uiState.value = _uiState.value.copy(
                    historicalCandles = series.candles,
                    isLoadingHistorical = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoadingHistorical = false,
                    error = "Historical data: ${e.message}"
                )
            }
        }
    }

    // ─── Available intervals ──────────────────────────────────────────────────

    fun loadAvailableIntervals(symbol: String = _uiState.value.selectedSymbol) {
        _uiState.value = _uiState.value.copy(isLoadingIntervals = true)
        viewModelScope.launch {
            restService.getAvailableIntervals(symbol)
                .onSuccess { intervals ->
                    _uiState.value = _uiState.value.copy(
                        availableIntervals = intervals,
                        isLoadingIntervals = false
                    )
                }.onFailure {
                    // Fall back to all known intervals
                    _uiState.value = _uiState.value.copy(
                        availableIntervals = CandleInterval.values().toList(),
                        isLoadingIntervals = false
                    )
                }
        }
    }

    // ─── Symbol search ────────────────────────────────────────────────────────

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        val q = query.uppercase()
        val cached = _uiState.value.allUsdtSymbols
        if (cached.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                searchResults = cached.filter { it.contains(q) }.take(20)
            )
        } else {
            _uiState.value = _uiState.value.copy(isSearching = true)
            viewModelScope.launch {
                restService.getUsdtSymbols()
                    .onSuccess { symbols ->
                        _uiState.value = _uiState.value.copy(
                            allUsdtSymbols = symbols,
                            searchResults = symbols.filter { it.contains(q) }.take(20),
                            isSearching = false
                        )
                    }.onFailure {
                        _uiState.value = _uiState.value.copy(isSearching = false)
                    }
            }
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchQuery = "", searchResults = emptyList())
    }

    // ─── Live collectors ──────────────────────────────────────────────────────

    private fun startCandleCollector() {
        candleCollectorJob?.cancel()
        candleCollectorJob = viewModelScope.launch {
            wsService.candleUpdates.collect { candle ->
                _uiState.value = _uiState.value.copy(liveCandle = candle)
                // If candle is closed, add it to the series
                if (candle.isClosed) {
                    val updated = (_uiState.value.candles + candle).takeLast(500)
                    _uiState.value = _uiState.value.copy(candles = updated, liveCandle = null)
                }
            }
        }
    }

    private fun startTradeCollector() {
        tradeCollectorJob?.cancel()
        tradeCollectorJob = viewModelScope.launch {
            wsService.tradeUpdates.collect { trade ->
                val updated = (listOf(trade) + _uiState.value.recentTrades).take(50)
                _uiState.value = _uiState.value.copy(recentTrades = updated)
            }
        }
    }

    // ─── Detail tab selection ─────────────────────────────────────────────────

    fun setDetailTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedDetailTab = tab)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refreshAll() {
        val sym = _uiState.value.selectedSymbol
        val interval = _uiState.value.selectedInterval
        loadOrderBook(sym)
        loadCandles(sym, interval)
    }

    override fun onCleared() {
        super.onCleared()
        wsService.disconnectAll()
    }
}
