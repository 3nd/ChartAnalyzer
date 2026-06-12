package com.chartanalyzer.app.models

// ─── Ticker / Price ───────────────────────────────────────────────────────────

data class TickerData(
    val symbol: String,
    val price: Double,
    val priceChange: Double,
    val priceChangePct: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val volume: Double,
    val quoteVolume: Double,
    val openPrice: Double,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isBullish: Boolean get() = priceChange >= 0
}

// ─── Order Book ───────────────────────────────────────────────────────────────

data class OrderBookEntry(
    val price: Double,
    val quantity: Double
)

data class OrderBookData(
    val symbol: String,
    val bids: List<OrderBookEntry>,   // sorted highest first
    val asks: List<OrderBookEntry>,   // sorted lowest first
    val lastUpdateId: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    val bestBid: Double get() = bids.firstOrNull()?.price ?: 0.0
    val bestAsk: Double get() = asks.firstOrNull()?.price ?: 0.0
    val spread: Double get() = bestAsk - bestBid
    val spreadPct: Double get() = if (bestAsk > 0) (spread / bestAsk) * 100 else 0.0
    val midPrice: Double get() = (bestBid + bestAsk) / 2.0

    val totalBidVolume: Double get() = bids.sumOf { it.quantity }
    val totalAskVolume: Double get() = asks.sumOf { it.quantity }
    val bidAskImbalance: Double
        get() {
            val total = totalBidVolume + totalAskVolume
            return if (total > 0) (totalBidVolume - totalAskVolume) / total else 0.0
        }
}

// ─── Candles / OHLCV ─────────────────────────────────────────────────────────

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long,
    val quoteVolume: Double,
    val trades: Int,
    val takerBuyVolume: Double,
    val takerBuyQuoteVolume: Double,
    val isClosed: Boolean = true
) {
    val isBullish: Boolean get() = close >= open
    val bodySize: Double get() = Math.abs(close - open)
    val range: Double get() = high - low
    val upperWick: Double get() = high - maxOf(open, close)
    val lowerWick: Double get() = minOf(open, close) - low
    val midpoint: Double get() = (high + low) / 2.0
}

data class CandleSeriesData(
    val symbol: String,
    val interval: String,
    val candles: List<Candle>
)

// ─── Candle Intervals ─────────────────────────────────────────────────────────

enum class CandleInterval(val code: String, val label: String, val displayName: String) {
    ONE_SECOND("1s", "1s", "1 Second"),
    ONE_MINUTE("1m", "1m", "1 Minute"),
    THREE_MINUTES("3m", "3m", "3 Minutes"),
    FIVE_MINUTES("5m", "5m", "5 Minutes"),
    FIFTEEN_MINUTES("15m", "15m", "15 Minutes"),
    THIRTY_MINUTES("30m", "30m", "30 Minutes"),
    ONE_HOUR("1h", "1h", "1 Hour"),
    TWO_HOURS("2h", "2h", "2 Hours"),
    FOUR_HOURS("4h", "4h", "4 Hours"),
    SIX_HOURS("6h", "6h", "6 Hours"),
    EIGHT_HOURS("8h", "8h", "8 Hours"),
    TWELVE_HOURS("12h", "12h", "12 Hours"),
    ONE_DAY("1d", "1D", "1 Day"),
    THREE_DAYS("3d", "3D", "3 Days"),
    ONE_WEEK("1w", "1W", "1 Week"),
    ONE_MONTH("1M", "1M", "1 Month");

    companion object {
        fun fromCode(code: String): CandleInterval =
            values().firstOrNull { it.code == code } ?: ONE_HOUR

        val intraday = listOf(ONE_MINUTE, THREE_MINUTES, FIVE_MINUTES, FIFTEEN_MINUTES, THIRTY_MINUTES)
        val hourly = listOf(ONE_HOUR, TWO_HOURS, FOUR_HOURS, SIX_HOURS, EIGHT_HOURS, TWELVE_HOURS)
        val daily = listOf(ONE_DAY, THREE_DAYS, ONE_WEEK, ONE_MONTH)
    }
}

// ─── Trade / Aggregated Trade ─────────────────────────────────────────────────

data class TradeData(
    val symbol: String,
    val tradeId: Long,
    val price: Double,
    val quantity: Double,
    val timestamp: Long,
    val isBuyerMaker: Boolean   // true = sell aggressor (ask hit), false = buy aggressor (bid hit)
) {
    val isBullish: Boolean get() = !isBuyerMaker  // buyer was aggressive
    val value: Double get() = price * quantity
}

// ─── Multi-ticker watchlist ───────────────────────────────────────────────────

data class WatchlistItem(
    val symbol: String,
    // Strip only the quote currency suffix — handles BTCUSDT→BTC, ETHBTC→ETH, etc.
    val displayName: String = symbol
        .let { s ->
            listOf("USDT","BUSD","BTC","ETH","BNB","TUSD","USDC","DAI","PAX")
                .firstOrNull { s.endsWith(it) }
                ?.let { suffix -> s.removeSuffix(suffix) } ?: s
        },
    val quoteCurrency: String = when {
        symbol.endsWith("USDT") -> "USDT"
        symbol.endsWith("BTC")  -> "BTC"
        symbol.endsWith("ETH")  -> "ETH"
        symbol.endsWith("BNB")  -> "BNB"
        else -> "USDT"
    }
)

// ─── Popular default symbols ─────────────────────────────────────────────────

object PopularSymbols {
    val defaults = listOf(
        WatchlistItem("BTCUSDT"),
        WatchlistItem("ETHUSDT"),
        WatchlistItem("BNBUSDT"),
        WatchlistItem("SOLUSDT"),
        WatchlistItem("XRPUSDT"),
        WatchlistItem("ADAUSDT"),
        WatchlistItem("DOGEUSDT"),
        WatchlistItem("AVAXUSDT"),
        WatchlistItem("DOTUSDT"),
        WatchlistItem("MATICUSDT"),
        WatchlistItem("LINKUSDT"),
        WatchlistItem("LTCUSDT"),
        WatchlistItem("ATOMUSDT"),
        WatchlistItem("UNIUSDT"),
        WatchlistItem("NEARUSDT")
    )

    val allSymbols: List<String> get() = defaults.map { it.symbol }
}

// ─── Historical data request ─────────────────────────────────────────────────

data class HistoricalDataRequest(
    val symbol: String,
    val interval: CandleInterval,
    val limit: Int = 500,          // max 1000 on Binance free tier
    val startTime: Long? = null,
    val endTime: Long? = null
)

// ─── Connection state ─────────────────────────────────────────────────────────

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class StreamStatus(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val subscribedSymbols: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val lastPingMs: Long = 0L
)
