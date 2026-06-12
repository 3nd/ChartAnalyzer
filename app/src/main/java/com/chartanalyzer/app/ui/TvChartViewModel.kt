package com.chartanalyzer.app.ui

import android.app.Application
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chartanalyzer.app.api.BinanceRestService
import com.chartanalyzer.app.api.BinanceWebSocketService
import com.chartanalyzer.app.api.CandleStateManager
import com.chartanalyzer.app.api.MLEnsembleEngine
import com.chartanalyzer.app.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

// ─── UI State for chart screen ────────────────────────────────────────────────

data class TvChartUiState(
    val symbol: String = "BTCUSDT",
    val interval: CandleInterval = CandleInterval.ONE_HOUR,
    val isLoading: Boolean = true,
    val isChartReady: Boolean = false,
    val error: String? = null,
    val streamStatus: StreamStatus = StreamStatus(),
    val lastSignal: PredictionSignal? = null,
    val chartConfig: ChartConfig = ChartConfig(),
    val availableIntervals: List<CandleInterval> = CandleInterval.values().toList(),
    val multiFrameActive: Boolean = false,
    val multiFrameIntervals: List<CandleInterval> = listOf(
        CandleInterval.FIVE_MINUTES,
        CandleInterval.ONE_HOUR,
        CandleInterval.FOUR_HOURS
    ),
    val showSettings: Boolean = false,
    val backtestMode: Boolean = false,
    // ── ML Prediction ──────────────────────────────────────────────────────────
    val mlPrediction: EnsemblePrediction? = null,
    val mlEnabled: Boolean = false,
    val mlHistory: List<EnsemblePrediction> = emptyList(),
    // ── Lunar / Moon Cycle ─────────────────────────────────────────────────────
    val lunarAge: Double = 0.0,
    val lunarIllumination: Double = 0.0,
    val lunarPhase: LunarCalculator.MoonPhase = LunarCalculator.MoonPhase.NEW_MOON,
    val lunarScore: Double = 0.0,
    val lunarReversalProximity: Double = 0.0,
    val lunarOverlayEnabled: Boolean = true,   // moon phase line visible on chart
    val daysToNextNewMoon: Double = 0.0,
    val daysToNextFullMoon: Double = 0.0
)

class TvChartViewModel(application: Application) : AndroidViewModel(application) {

    private val wsService    = BinanceWebSocketService()
    private val restService  = BinanceRestService()
    private val stateManager = CandleStateManager()
    val mlEngine             = MLEnsembleEngine(application)   // shared with MLViewModel

    private val _uiState = MutableStateFlow(TvChartUiState())
    val uiState: StateFlow<TvChartUiState> = _uiState.asStateFlow()

    private var webViewRef: WebView? = null
    private var candleJob: Job? = null
    private var liveJob: Job? = null
    private var mlJob: Job? = null
    private var pendingSymbol: String? = null   // buffered until WebView is ready

    // ─── Called from WebView JS via JavascriptInterface ───────────────────────

    @JavascriptInterface
    fun onChartReady() {
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(isChartReady = true)
            // Load pending symbol if one was set before the chart was ready,
            // otherwise fall back to current state symbol
            val sym = pendingSymbol ?: _uiState.value.symbol
            pendingSymbol = null
            loadAndPushChartData(sym, _uiState.value.interval)
        }
    }

    @JavascriptInterface
    fun onTimeframeChange(tf: String) {
        val interval = CandleInterval.fromCode(tf)
        viewModelScope.launch(Dispatchers.Main) { changeInterval(interval) }
    }

    // ─── WebView attachment ────────────────────────────────────────────────────

    fun attachWebView(wv: WebView) {
        webViewRef = wv
        // Drain any queued JS commands now that WebView is ready
        drainJsQueue(wv)
        // Stream status
        viewModelScope.launch {
            wsService.streamStatus.collect { status ->
                _uiState.value = _uiState.value.copy(streamStatus = status)
            }
        }
        // ML prediction collector — push to chart whenever a new prediction arrives
        mlJob?.cancel()
        mlJob = viewModelScope.launch {
            mlEngine.latestPrediction.collect { pred ->
                if (pred != null && _uiState.value.mlEnabled) {
                    val history = (listOf(pred) + _uiState.value.mlHistory).take(100)
                    _uiState.value = _uiState.value.copy(
                        mlPrediction = pred,
                        mlHistory = history
                    )
                    pushMlPredictionToChart(pred)
                }
            }
        }
    }

    fun detachWebView() {
        webViewRef = null
    }

    // ─── ML overlay toggle ────────────────────────────────────────────────────

    fun toggleMlOverlay() {
        val next = !_uiState.value.mlEnabled
        _uiState.value = _uiState.value.copy(mlEnabled = next)
        postJs("setMlLayerVisible(${next})")
        if (next) {
            _uiState.value.mlHistory.takeLast(50).forEach { pred ->
                pushMlPredictionToChart(pred)
            }
            _uiState.value.mlPrediction?.let { pushMlPredictionToChart(it) }
        }
    }

    fun toggleLunarOverlay() {
        val next = !_uiState.value.lunarOverlayEnabled
        _uiState.value = _uiState.value.copy(lunarOverlayEnabled = next)
        postJs("setLunarLayerVisible(${next})")
    }

    // ─── Feed new closed candle to ML engine ──────────────────────────────────

    private fun feedCandleToMl(candle: Candle, allCandles: List<Candle>) {
        if (_uiState.value.mlEnabled && mlEngine.isModelTrained) {
            mlEngine.onNewCandle(candle, allCandles)
        }
    }

    // ─── Load symbol ──────────────────────────────────────────────────────────

    fun loadSymbol(symbol: String, interval: CandleInterval = _uiState.value.interval) {
        _uiState.value = _uiState.value.copy(
            symbol = symbol, interval = interval,
            isLoading = true, error = null, lastSignal = null
        )
        if (!_uiState.value.isChartReady || webViewRef == null) {
            // Chart not ready yet — buffer the symbol; onChartReady() will load it
            pendingSymbol = symbol
        } else {
            viewModelScope.launch { loadAndPushChartData(symbol, interval) }
        }
    }

    fun changeInterval(interval: CandleInterval) {
        val sym = _uiState.value.symbol
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(interval = interval, isLoading = true)
            loadAndPushChartData(sym, interval)
        }
    }

    // ─── Core: fetch history, init state manager, start WS, push to chart ────

    private suspend fun loadAndPushChartData(symbol: String, interval: CandleInterval) {
        // Stop previous streams
        candleJob?.cancel()
        liveJob?.cancel()
        stateManager.clear(symbol, interval)

        // Fetch 300 historical candles via REST
        val result = restService.getHistoricalCandles(
            HistoricalDataRequest(symbol, interval, limit = 300)
        )
        result.onFailure { e ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Failed to load candles: ${e.message}"
            )
            return
        }

        val series = result.getOrNull() ?: return
        stateManager.initWithHistory(symbol, interval, series.candles)
        val state = stateManager.getState(symbol, interval)!!

        // Push initial full payload to chart
        pushFullChartData(state)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            lastSignal = state.lastSignal,
            lunarAge = state.lunarAge,
            lunarIllumination = state.lunarIllumination,
            lunarPhase = state.lunarPhase,
            lunarScore = state.lunarScore,
            lunarReversalProximity = state.lunarReversalProximity,
            daysToNextNewMoon = LunarCalculator.daysToNextNewMoon(state.lunarAge),
            daysToNextFullMoon = LunarCalculator.daysToNextFullMoon(state.lunarAge)
        )

        // Subscribe to live candle WebSocket
        wsService.subscribeToCandleStream(symbol, interval)

        // Collect live ticks (non-closed) → update candle display only
        liveJob = viewModelScope.launch {
            wsService.candleUpdates.collect { candle ->
                if (!candle.isClosed) {
                    stateManager.processCandle(symbol, interval, candle)
                    // Push lightweight tick update (no signal recalc)
                    val json = JSONObject().apply {
                        put("candle", JSONObject().apply {
                            put("t", candle.openTime / 1000L)
                            put("o", candle.open);  put("h", candle.high)
                            put("l", candle.low);   put("c", candle.close)
                            put("v", candle.volume)
                        })
                    }
                    postJs("updateCandle(${json.toSafeJsArg()})")
                }
            }
        }

        // Collect closed candles → full signal pipeline → push to chart
        candleJob = viewModelScope.launch {
            stateManager.closedCandleFlow.collect { updatedState ->
                if (updatedState.symbol == symbol && updatedState.interval == interval) {
                    _uiState.value = _uiState.value.copy(lastSignal = updatedState.lastSignal)
                    pushCandleCloseUpdate(updatedState)
                }
            }
        }

        // Feed incoming closed candles from WS into state manager + ML engine
        viewModelScope.launch {
            wsService.candleUpdates.collect { candle ->
                if (candle.isClosed) {
                    stateManager.processCandle(symbol, interval, candle)
                    // Feed to ML engine for real-time inference
                    val allCandles = stateManager.getState(symbol, interval)?.confirmedCandles
                        ?: emptyList()
                    feedCandleToMl(candle, allCandles)
                }
            }
        }
    }

    // ─── JS payload builders ──────────────────────────────────────────────────

    private fun pushFullChartData(state: CandleState) {
        val json = JSONObject()
        json.put("symbol", state.symbol)
        json.put("interval", state.interval.code)

        // Candles array: [{t, o, h, l, c, v}]
        val candlesArr = JSONArray()
        state.confirmedCandles.forEach { c ->
            candlesArr.put(JSONObject().apply {
                put("t", c.openTime / 1000L)
                put("o", c.open); put("h", c.high)
                put("l", c.low);  put("c", c.close)
                put("v", c.volume)
            })
        }
        json.put("candles", candlesArr)

        // Signal series: [[timestampSec, value]]
        json.put("buyStrength",  seriesArray(state.buyStrengthSeries))
        json.put("sellStrength", seriesArray(state.sellStrengthSeries))
        json.put("confidence",   seriesArray(state.confidenceSeries))

        // Signal markers
        val sigArr = JSONArray()
        state.allSignals.filter { it.type != SignalType.NEUTRAL }.forEach { s ->
            sigArr.put(JSONObject().apply {
                put("t", s.timestamp / 1000L)
                put("type", s.type.name)
                put("conf", s.confidence)
                put("buy", s.buyStrength)
                put("sell", s.sellStrength)
                put("source", s.source.name)
                put("price", s.price)
            })
        }
        json.put("signals", sigArr)

        // Price zones
        val zonesArr = JSONArray()
        state.priceZones.forEach { z ->
            zonesArr.put(JSONObject().apply {
                put("id", z.id)
                put("price", z.price)
                put("color", z.color)
                put("label", z.label)
                put("width", z.lineWidth)
                put("dash", z.lineDash)
            })
        }
        json.put("zones", zonesArr)

        // Last signal for info box
        state.lastSignal?.let { sig ->
            json.put("lastSignal", signalJson(sig))
        }

        // ── Lunar data ──────────────────────────────────────────────────────────
        // Moon phase series: [[timestampSec, phaseAngle 0-360]]
        json.put("moonPhase",  seriesArray(state.moonPhaseSeries))
        json.put("moonIllum",  seriesArray(state.moonIlluminationSeries))

        // Current lunar snapshot for the info box
        json.put("lunar", JSONObject().apply {
            put("age",      state.lunarAge)
            put("illum",    state.lunarIllumination * 100.0)
            put("score",    state.lunarScore)
            put("revProx",  state.lunarReversalProximity)
            put("phase",    state.lunarPhase.label)
            put("emoji",    state.lunarPhase.emoji)
            put("isWaxing", LunarCalculator.isWaxing(state.lunarAge))
            put("daysToNew",  LunarCalculator.daysToNextNewMoon(state.lunarAge))
            put("daysToFull", LunarCalculator.daysToNextFullMoon(state.lunarAge))
        })

        postJs("setChartData(${json.toSafeJsArg()})")
    }

    private fun pushCandleCloseUpdate(state: CandleState) {
        val sig = state.lastSignal ?: return
        val newCandle = state.confirmedCandles.lastOrNull() ?: return

        val json = JSONObject()

        // The newly closed candle
        json.put("candle", JSONObject().apply {
            put("t", newCandle.openTime / 1000L)
            put("o", newCandle.open); put("h", newCandle.high)
            put("l", newCandle.low);  put("c", newCandle.close)
            put("v", newCandle.volume)
        })

        // Last signal series point
        json.put("buyStrength",  JSONArray().apply { put(sig.timestamp / 1000L); put(sig.buyStrength) })
        json.put("sellStrength", JSONArray().apply { put(sig.timestamp / 1000L); put(sig.sellStrength) })
        json.put("confidence",   JSONArray().apply { put(sig.timestamp / 1000L); put(sig.confidence * 100.0) })

        // Signal marker if non-neutral
        if (sig.type != SignalType.NEUTRAL) {
            json.put("signal", signalJson(sig))
        }

        // Updated zones (sent every close)
        val zonesArr = JSONArray()
        state.priceZones.forEach { z ->
            zonesArr.put(JSONObject().apply {
                put("id", z.id)
                put("price", z.price)
                put("color", z.color)
                put("label", z.label)
                put("width", z.lineWidth)
                put("dash", z.lineDash)
            })
        }
        json.put("zones", zonesArr)

        // Lunar update on each closed candle
        json.put("lunar", JSONObject().apply {
            put("age",      state.lunarAge)
            put("illum",    state.lunarIllumination * 100.0)
            put("score",    state.lunarScore)
            put("revProx",  state.lunarReversalProximity)
            put("phase",    state.lunarPhase.label)
            put("emoji",    state.lunarPhase.emoji)
            put("isWaxing", LunarCalculator.isWaxing(state.lunarAge))
            put("daysToNew",  LunarCalculator.daysToNextNewMoon(state.lunarAge))
            put("daysToFull", LunarCalculator.daysToNextFullMoon(state.lunarAge))
        })
        // Latest lunar series point
        json.put("moonPhasePoint", JSONArray().apply {
            put(newCandle.openTime / 1000L)
            put(LunarCalculator.phaseAngle(state.lunarAge))
        })
        json.put("moonIllumPoint", JSONArray().apply {
            put(newCandle.openTime / 1000L)
            put(state.lunarIllumination * 100.0)
        })

        postJs("onCandleClose(${json.toSafeJsArg()})")
    }

    // ─── Safe JSON→JS: pass via a global temp variable to avoid quoting issues ──
    // Any special characters in the JSON (quotes, backticks, newlines) break
    // JSON.parse('...') string literals. Assigning via a variable is safe.
    private fun JSONObject.toSafeJsArg(): String {
        // Escape only backslashes and backticks for safety in template literal
        val escaped = toString()
            .replace("\\", "\\\\")
            .replace("`", "\\`")
        return "JSON.parse(`$escaped`)"
    }

    private fun signalJson(sig: PredictionSignal): JSONObject = JSONObject().apply {
        put("t", sig.timestamp / 1000L)
        put("type", sig.type.name)
        put("conf", sig.confidence)
        put("buyStrength", sig.buyStrength)
        put("sellStrength", sig.sellStrength)
        put("source", sig.source.name)
        put("price", sig.price)
        sig.targetPrice?.let { put("target", it) }
        sig.stopLoss?.let { put("stop", it) }
        sig.riskReward?.let { put("rr", it) }
        put("notes", sig.notes)
    }

    private fun seriesArray(pairs: List<Pair<Long, Double>>): JSONArray {
        val arr = JSONArray()
        pairs.forEach { (t, v) ->
            arr.put(JSONArray().apply { put(t); put(v) })
        }
        return arr
    }

    // ─── Multi-timeframe ──────────────────────────────────────────────────────

    fun toggleMultiFrame() {
        val next = !_uiState.value.multiFrameActive
        _uiState.value = _uiState.value.copy(multiFrameActive = next)
        if (next) {
            // Pre-load all multi-frame intervals
            _uiState.value.multiFrameIntervals.forEach { interval ->
                viewModelScope.launch {
                    val sym = _uiState.value.symbol
                    val result = restService.getHistoricalCandles(
                        HistoricalDataRequest(sym, interval, limit = 200)
                    )
                    result.onSuccess { series ->
                        stateManager.initWithHistory(sym, interval, series.candles)
                    }
                }
            }
        }
    }

    fun getMultiFrameSignals(): Map<CandleInterval, PredictionSignal?> {
        return _uiState.value.multiFrameIntervals.associate { interval ->
            interval to stateManager.getState(_uiState.value.symbol, interval)?.lastSignal
        }
    }

    // ─── Config ───────────────────────────────────────────────────────────────

    fun updateConfig(config: ChartConfig) {
        _uiState.value = _uiState.value.copy(chartConfig = config)
    }

    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(showSettings = !_uiState.value.showSettings)
    }

    fun toggleBacktest() {
        _uiState.value = _uiState.value.copy(backtestMode = !_uiState.value.backtestMode)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ─── ML prediction → chart JS bridge ─────────────────────────────────────

    /**
     * Push a single new EnsemblePrediction to the chart as:
     *  - A marker on the candlestick series (BUY/SELL arrow)
     *  - A new data point on the GB probability line
     *  - A new data point on the LSTM probability line
     *  - A new data point on the ensemble confidence line
     *  - Entry/Target/Stop price lines
     */
    private fun pushMlPredictionToChart(pred: EnsemblePrediction) {
        val json = buildMlPredictionJson(pred)
        postJs("onMlPrediction($json)")
    }

    /**
     * Push full ML history to chart (called when ML overlay is enabled after training).
     */
    fun pushMlHistoryToChart() {
        val history = _uiState.value.mlHistory
        if (history.isEmpty()) return
        val arr = JSONArray()
        history.forEach { pred -> arr.put(JSONObject(buildMlPredictionJson(pred))) }
        postJs("setMlHistory($arr)")
    }

    private fun buildMlPredictionJson(pred: EnsemblePrediction): String {
        val tSec = pred.timestamp / 1000L
        return JSONObject().apply {
            put("t", tSec)
            put("price", pred.price)
            put("signal", pred.signal.name)
            put("buyPct", pred.buyProbability)
            put("sellPct", pred.sellProbability)
            put("holdPct", pred.holdProbability)
            put("confidence", pred.confidence * 100.0)
            put("agreement", pred.agreement * 100.0)
            put("gbBuy",   pred.gbPrediction.buyProbability   * 100.0)
            put("gbSell",  pred.gbPrediction.sellProbability  * 100.0)
            put("lstmBuy",  pred.lstmPrediction.buyProbability  * 100.0)
            put("lstmSell", pred.lstmPrediction.sellProbability * 100.0)
            put("notes", pred.notes)

            // Top features from GB (feature importance)
            val featArr = JSONArray()
            pred.topFeatures.take(5).forEach { (name, score) ->
                featArr.put(JSONObject().apply {
                    put("name", name)
                    put("score", score)
                })
            }
            put("topFeatures", featArr)

            // Optional price targets (derived from price ± ATR proxy)
            val atrProxy = pred.price * 0.02  // 2% as rough ATR proxy if unavailable
            if (pred.signal == SignalType.BUY || pred.signal == SignalType.STRONG_BUY) {
                put("target", pred.price + atrProxy * 2.0)
                put("stop",   pred.price - atrProxy * 1.0)
            } else if (pred.signal == SignalType.SELL || pred.signal == SignalType.STRONG_SELL) {
                put("target", pred.price - atrProxy * 2.0)
                put("stop",   pred.price + atrProxy * 1.0)
            }
        }.toString()
    }

    // Queue of JS commands — replayed when WebView attaches
    private val _jsQueue = ArrayDeque<String>()

    // ─── Post JS to WebView on main thread — safe JSON encoding ──────────────

    private fun postJs(js: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val wv = webViewRef
            if (wv != null) {
                wv.evaluateJavascript(js, null)
            } else {
                _jsQueue.addLast(js)
            }
        }
    }

    /** Called by TvChartWebView after the WebView attaches — drains pending commands */
    fun drainJsQueue(wv: WebView) {
        viewModelScope.launch(Dispatchers.Main) {
            while (_jsQueue.isNotEmpty()) {
                wv.evaluateJavascript(_jsQueue.removeFirst(), null)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mlJob?.cancel()
        wsService.disconnectAll()
        stateManager.clearAll()
        webViewRef = null
    }
}
