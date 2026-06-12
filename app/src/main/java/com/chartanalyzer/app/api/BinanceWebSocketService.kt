package com.chartanalyzer.app.api

import android.util.Log
import com.chartanalyzer.app.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Binance WebSocket Service
 * Public streams — no authentication required.
 * Docs: https://binance-docs.github.io/apidocs/spot/en/#websocket-market-streams
 */
class BinanceWebSocketService {

    companion object {
        private const val TAG = "BinanceWS"
        private const val WS_BASE = "wss://stream.binance.com:9443/stream"
        private const val SINGLE_BASE = "wss://stream.binance.com:9443/ws"
        private const val PING_INTERVAL_MS = 25_000L
        private const val RECONNECT_DELAY_MS = 3_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // no timeout — persistent connection
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── State flows ──────────────────────────────────────────────────────────

    private val _streamStatus = MutableStateFlow(StreamStatus())
    val streamStatus: StateFlow<StreamStatus> = _streamStatus.asStateFlow()

    private val _tickerUpdates = MutableSharedFlow<TickerData>(replay = 0, extraBufferCapacity = 128)
    val tickerUpdates: SharedFlow<TickerData> = _tickerUpdates.asSharedFlow()

    private val _orderBookUpdates = MutableSharedFlow<OrderBookData>(replay = 1, extraBufferCapacity = 32)
    val orderBookUpdates: SharedFlow<OrderBookData> = _orderBookUpdates.asSharedFlow()

    private val _candleUpdates = MutableSharedFlow<Candle>(replay = 0, extraBufferCapacity = 64)
    val candleUpdates: SharedFlow<Candle> = _candleUpdates.asSharedFlow()

    private val _tradeUpdates = MutableSharedFlow<TradeData>(replay = 0, extraBufferCapacity = 256)
    val tradeUpdates: SharedFlow<TradeData> = _tradeUpdates.asSharedFlow()

    // ─── Internal state ───────────────────────────────────────────────────────

    private var combinedSocket: WebSocket? = null
    private var orderBookSocket: WebSocket? = null
    private var candleSocket: WebSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var reconnectAttempts = 0
    private var subscribedSymbols = mutableSetOf<String>()

    // ─── Multi-symbol ticker stream (combined stream) ─────────────────────────

    fun subscribeToTickers(symbols: List<String>) {
        subscribedSymbols.clear()
        subscribedSymbols.addAll(symbols.map { it.uppercase() })
        connectCombinedStream()
    }

    fun addSymbol(symbol: String) {
        subscribedSymbols.add(symbol.uppercase())
        connectCombinedStream()
    }

    fun removeSymbol(symbol: String) {
        subscribedSymbols.remove(symbol.uppercase())
        if (subscribedSymbols.isEmpty()) {
            combinedSocket?.close(1000, "No symbols")
            combinedSocket = null
        } else {
            connectCombinedStream()
        }
    }

    private fun connectCombinedStream() {
        combinedSocket?.close(1000, "Reconnecting")
        combinedSocket = null
        if (subscribedSymbols.isEmpty()) return

        // Each symbol gets a miniTicker stream: <symbol>@miniTicker
        val streams = subscribedSymbols.joinToString("/") { sym ->
            "${sym.lowercase()}@miniTicker"
        }
        val url = "$WS_BASE?streams=$streams"

        _streamStatus.value = _streamStatus.value.copy(
            state = ConnectionState.CONNECTING,
            subscribedSymbols = subscribedSymbols.toSet()
        )

        val request = Request.Builder().url(url).build()
        combinedSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Combined stream opened: $streams")
                isRunning.set(true)
                reconnectAttempts = 0
                _streamStatus.value = _streamStatus.value.copy(
                    state = ConnectionState.CONNECTED,
                    subscribedSymbols = subscribedSymbols.toSet(),
                    errorMessage = null
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseCombinedMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Combined stream failure: ${t.message}")
                handleReconnect("Combined stream: ${t.message}") { connectCombinedStream() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Combined stream closed: $reason")
                if (isRunning.get() && code != 1000) {
                    handleReconnect("Closed: $reason") { connectCombinedStream() }
                } else {
                    _streamStatus.value = _streamStatus.value.copy(state = ConnectionState.DISCONNECTED)
                }
            }
        })
    }

    private fun parseCombinedMessage(text: String) {
        try {
            val json = JSONObject(text)
            val data = json.getJSONObject("data")
            val eventType = data.optString("e")
            when (eventType) {
                "24hrMiniTicker" -> parseMiniTicker(data)
                "24hrTicker" -> parseFullTicker(data)
                else -> { /* ignore */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse error combined: ${e.message}")
        }
    }

    private fun parseMiniTicker(data: JSONObject) {
        try {
            val ticker = TickerData(
                symbol = data.getString("s"),
                price = data.getDouble("c"),
                priceChange = data.getDouble("c") - data.getDouble("o"),
                priceChangePct = if (data.getDouble("o") > 0)
                    ((data.getDouble("c") - data.getDouble("o")) / data.getDouble("o")) * 100.0
                else 0.0,
                highPrice = data.getDouble("h"),
                lowPrice = data.getDouble("l"),
                volume = data.getDouble("v"),
                quoteVolume = data.getDouble("q"),
                openPrice = data.getDouble("o")
            )
            scope.launch { _tickerUpdates.emit(ticker) }
        } catch (e: Exception) {
            Log.w(TAG, "Parse miniTicker error: ${e.message}")
        }
    }

    private fun parseFullTicker(data: JSONObject) {
        try {
            val ticker = TickerData(
                symbol = data.getString("s"),
                price = data.getDouble("c"),
                priceChange = data.getDouble("p"),
                priceChangePct = data.getDouble("P"),
                highPrice = data.getDouble("h"),
                lowPrice = data.getDouble("l"),
                volume = data.getDouble("v"),
                quoteVolume = data.getDouble("q"),
                openPrice = data.getDouble("o")
            )
            scope.launch { _tickerUpdates.emit(ticker) }
        } catch (e: Exception) {
            Log.w(TAG, "Parse fullTicker error: ${e.message}")
        }
    }

    // ─── Individual symbol — order book depth stream ──────────────────────────

    fun subscribeToOrderBook(symbol: String, depth: Int = 20) {
        orderBookSocket?.close(1000, "New subscription")
        val sym = symbol.lowercase()
        val levels = when {
            depth <= 5 -> 5
            depth <= 10 -> 10
            else -> 20
        }
        val url = "$SINGLE_BASE/${sym}@depth${levels}@100ms"
        val request = Request.Builder().url(url).build()

        orderBookSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "OrderBook stream opened for $symbol")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseOrderBook(symbol.uppercase(), text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "OrderBook failure: ${t.message}")
                handleReconnect("OrderBook: ${t.message}") { subscribeToOrderBook(symbol, depth) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code != 1000) handleReconnect(reason) { subscribeToOrderBook(symbol, depth) }
            }
        })
    }

    private fun parseOrderBook(symbol: String, text: String) {
        try {
            val json = JSONObject(text)
            val lastUpdateId = json.getLong("lastUpdateId")

            fun parseEntries(arr: JSONArray): List<OrderBookEntry> {
                val list = mutableListOf<OrderBookEntry>()
                for (i in 0 until arr.length()) {
                    val entry = arr.getJSONArray(i)
                    list.add(OrderBookEntry(
                        price = entry.getDouble(0),
                        quantity = entry.getDouble(1)
                    ))
                }
                return list
            }

            val bids = parseEntries(json.getJSONArray("bids"))
                .sortedByDescending { it.price }
            val asks = parseEntries(json.getJSONArray("asks"))
                .sortedBy { it.price }

            val obData = OrderBookData(
                symbol = symbol,
                bids = bids,
                asks = asks,
                lastUpdateId = lastUpdateId
            )
            scope.launch { _orderBookUpdates.emit(obData) }
        } catch (e: Exception) {
            Log.w(TAG, "Parse orderBook error: ${e.message}")
        }
    }

    fun stopOrderBook() {
        orderBookSocket?.close(1000, "Stopped")
        orderBookSocket = null
    }

    // ─── Individual symbol — candle/kline stream ──────────────────────────────

    fun subscribeToCandleStream(symbol: String, interval: CandleInterval) {
        candleSocket?.close(1000, "New subscription")
        val sym = symbol.lowercase()
        val url = "$SINGLE_BASE/${sym}@kline_${interval.code}"
        val request = Request.Builder().url(url).build()

        candleSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Candle stream opened: $symbol ${interval.code}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseCandleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleReconnect("Candle: ${t.message}") { subscribeToCandleStream(symbol, interval) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code != 1000) handleReconnect(reason) { subscribeToCandleStream(symbol, interval) }
            }
        })
    }

    private fun parseCandleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val k = json.getJSONObject("k")
            val candle = Candle(
                openTime = k.getLong("t"),
                open = k.getDouble("o"),
                high = k.getDouble("h"),
                low = k.getDouble("l"),
                close = k.getDouble("c"),
                volume = k.getDouble("v"),
                closeTime = k.getLong("T"),
                quoteVolume = k.getDouble("q"),
                trades = k.getInt("n"),
                takerBuyVolume = k.getDouble("V"),
                takerBuyQuoteVolume = k.getDouble("Q"),
                isClosed = k.getBoolean("x")
            )
            scope.launch { _candleUpdates.emit(candle) }
        } catch (e: Exception) {
            Log.w(TAG, "Parse candle error: ${e.message}")
        }
    }

    fun stopCandleStream() {
        candleSocket?.close(1000, "Stopped")
        candleSocket = null
    }

    // ─── Individual symbol — aggregate trade stream ───────────────────────────

    private var tradeSocket: WebSocket? = null

    fun subscribeToTrades(symbol: String) {
        tradeSocket?.close(1000, "New subscription")
        val sym = symbol.lowercase()
        val url = "$SINGLE_BASE/${sym}@aggTrade"
        val request = Request.Builder().url(url).build()

        tradeSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                parseAggTrade(symbol.uppercase(), text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleReconnect("Trades: ${t.message}") { subscribeToTrades(symbol) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code != 1000) handleReconnect(reason) { subscribeToTrades(symbol) }
            }
        })
    }

    private fun parseAggTrade(symbol: String, text: String) {
        try {
            val json = JSONObject(text)
            val trade = TradeData(
                symbol = symbol,
                tradeId = json.getLong("a"),
                price = json.getDouble("p"),
                quantity = json.getDouble("q"),
                timestamp = json.getLong("T"),
                isBuyerMaker = json.getBoolean("m")
            )
            scope.launch { _tradeUpdates.emit(trade) }
        } catch (e: Exception) {
            Log.w(TAG, "Parse aggTrade error: ${e.message}")
        }
    }

    fun stopTrades() {
        tradeSocket?.close(1000, "Stopped")
        tradeSocket = null
    }

    // ─── Reconnect logic ──────────────────────────────────────────────────────

    private fun handleReconnect(reason: String, reconnectFn: () -> Unit) {
        _streamStatus.value = _streamStatus.value.copy(
            state = ConnectionState.RECONNECTING,
            errorMessage = reason
        )
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            scope.launch {
                delay(RECONNECT_DELAY_MS * reconnectAttempts)
                reconnectFn()
            }
        } else {
            _streamStatus.value = _streamStatus.value.copy(
                state = ConnectionState.ERROR,
                errorMessage = "Max reconnect attempts reached: $reason"
            )
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    fun disconnectAll() {
        isRunning.set(false)
        combinedSocket?.close(1000, "App disconnect")
        orderBookSocket?.close(1000, "App disconnect")
        candleSocket?.close(1000, "App disconnect")
        tradeSocket?.close(1000, "App disconnect")
        combinedSocket = null
        orderBookSocket = null
        candleSocket = null
        tradeSocket = null
        subscribedSymbols.clear()
        scope.cancel()
        _streamStatus.value = StreamStatus(state = ConnectionState.DISCONNECTED)
    }
}
