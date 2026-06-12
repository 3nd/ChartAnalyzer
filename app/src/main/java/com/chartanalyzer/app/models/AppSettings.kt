package com.chartanalyzer.app.models

// ─── Market Data Providers ────────────────────────────────────────────────────

enum class MarketDataProvider(
    val id: String,
    val displayName: String,
    val description: String,
    val restBaseUrl: String,
    val wsBaseUrl: String,
    val requiresAuth: Boolean,
    val emoji: String,
    val accentHex: String,
    val isDefault: Boolean = false
) {
    BINANCE(
        id = "binance",
        displayName = "Binance",
        description = "World's largest crypto exchange. No authentication required for market data. Full candlestick, ticker, and order book streaming via WebSocket.",
        restBaseUrl = "https://api.binance.com/api/v3",
        wsBaseUrl   = "wss://stream.binance.com:9443/ws",
        requiresAuth = false,
        emoji = "🟡",
        accentHex = "#F0B90B",
        isDefault = true
    ),
    OKX(
        id = "okx",
        displayName = "OKX",
        description = "Top-3 global crypto exchange. Public WebSocket available for candlesticks, tickers, and order books. No API key required for market data.",
        restBaseUrl = "https://www.okx.com/api/v5",
        wsBaseUrl   = "wss://ws.okx.com:8443/ws/v5/public",
        requiresAuth = false,
        emoji = "⚫",
        accentHex   = "#121212"
    ),
    COINBASE(
        id = "coinbase",
        displayName = "Coinbase",
        description = "US-regulated exchange with reliable data feeds. Coinbase Advanced Trade WebSocket provides real-time candles and ticker data with no authentication for public channels.",
        restBaseUrl = "https://api.exchange.coinbase.com",
        wsBaseUrl   = "wss://advanced-trade-ws.coinbase.com",
        requiresAuth = false,
        emoji = "🔵",
        accentHex   = "#1652F0"
    ),
    KRAKEN(
        id = "kraken",
        displayName = "Kraken",
        description = "Long-standing US crypto exchange with a robust public WebSocket API for OHLC, tickers, and order books. No authentication required for public market data.",
        restBaseUrl = "https://api.kraken.com/0/public",
        wsBaseUrl   = "wss://ws.kraken.com",
        requiresAuth = false,
        emoji = "🦑",
        accentHex   = "#5741D9"
    ),
    COINGECKO(
        id = "coingecko",
        displayName = "CoinGecko",
        description = "Aggregated market data for 10,000+ coins. Free tier: 30 calls/min REST API. Provides price, volume, market cap, and rank data. Note: REST-only (no WebSocket on free tier).",
        restBaseUrl = "https://api.coingecko.com/api/v3",
        wsBaseUrl   = "",   // REST-only on free tier
        requiresAuth = false,
        emoji = "🦎",
        accentHex   = "#8CC63F"
    ),
    FINNHUB(
        id = "finnhub",
        displayName = "Finnhub",
        description = "Real-time stock, forex, and crypto WebSocket feed. Free tier includes crypto trades WebSocket and REST candles. No cost for basic market data.",
        restBaseUrl = "https://finnhub.io/api/v1",
        wsBaseUrl   = "wss://ws.finnhub.io",
        requiresAuth = false,   // API key in URL but free tier is open
        emoji = "📈",
        accentHex   = "#1DB954"
    )
}

// ─── App Settings ─────────────────────────────────────────────────────────────

data class AppSettings(
    val marketDataProvider: MarketDataProvider = MarketDataProvider.BINANCE,
    val networkLogEnabled: Boolean = false,
    val stateLogEnabled: Boolean = false
)

// ─── Application Log ──────────────────────────────────────────────────────────

enum class LogLevel { NETWORK, STATE, ERROR, INFO }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String
)

// ─── Changelog ────────────────────────────────────────────────────────────────

data class ChangelogEntry(
    val version: String,
    val date: String,
    val type: String,       // "Major", "Minor", "Patch"
    val changes: List<String>
)

object AppChangelog {
    val entries = listOf(
        ChangelogEntry(
            version = "2.0",
            date    = "2025",
            type    = "Major",
            changes = listOf(
                "Application Settings page with version info, data provider switcher, changelog, and app log",
                "Market data provider switcher: Binance (default), OKX, Coinbase, Kraken, CoinGecko, Finnhub",
                "Network Trace and State Log with save-to-file and copy-to-clipboard",
                "AI Providers: added ChatGPT Free, xAI Grok Free, GenAI.mil; removed Bard AI (now Gemini)",
                "Analyze tab: fixed chart loading bug (el() hoisting in chart.html + JS safe arg passing)",
                "Analyze tab: Quick Questions dropdown now numbered and scrollable",
                "Analyze tab: Analyze button redesigned — mint green (#3DAC78), left/right layout",
                "Markets tab: cryptocurrency rank and emoji icon per coin",
                "Markets tab: configurable % change period (1h, 24h, 7d, 30d)",
                "Markets tab: sort by rank, price, or % change",
                "ML Engine: independent GB and LSTM toggle switches",
                "ML Engine: Moon Cycle and Shemitah Cycle enhancer toggle switches",
                "56 ML features: 45 TA + 6 lunar + 5 Shemitah"
            )
        ),
        ChangelogEntry(
            version = "1.9.2",
            date    = "2025",
            type    = "Minor",
            changes = listOf(
                "Fixed chart loading race condition (pending symbol buffer + JS queue drain)",
                "Fixed JS injection of special characters in chart data",
                "BTC label fix in Markets watchlist",
                "Persistent global header across all tabs",
                "Quick Questions merged into Your Question section",
                "Raw chart data toggle for text-only AI providers",
                "Saved questions with delete capability"
            )
        ),
        ChangelogEntry(
            version = "1.9.1",
            date    = "2025",
            type    = "Minor",
            changes = listOf(
                "Two-button chart source selector (ticker + upload)",
                "Crypto ticker selector dialog",
                "Formulas merged into Frameworks tab as expandable rows",
                "Standalone Formulas tab removed",
                "Android 16 edge-to-edge insets fixed across all screens"
            )
        )
    )
}
