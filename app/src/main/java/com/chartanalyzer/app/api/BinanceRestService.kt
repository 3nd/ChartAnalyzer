package com.chartanalyzer.app.api

import android.util.Log
import com.chartanalyzer.app.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Binance REST API Service — public endpoints, no authentication required.
 * Base: https://api.binance.com/api/v3
 * Docs: https://binance-docs.github.io/apidocs/spot/en/
 */
class BinanceRestService {

    companion object {
        private const val TAG = "BinanceREST"
        private const val BASE_URL = "https://api.binance.com/api/v3"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ─── Generic fetch ────────────────────────────────────────────────────────

    private suspend fun get(endpoint: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$BASE_URL$endpoint").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(body)
            } else {
                val errMsg = try {
                    JSONObject(body).optString("msg", "HTTP ${response.code}")
                } catch (_: Exception) { "HTTP ${response.code}" }
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET error: $endpoint — ${e.message}")
            Result.failure(e)
        }
    }

    // ─── 1. Historical candles (klines) ──────────────────────────────────────

    suspend fun getHistoricalCandles(request: HistoricalDataRequest): Result<CandleSeriesData> {
        val params = buildString {
            append("/klines?symbol=${request.symbol.uppercase()}")
            append("&interval=${request.interval.code}")
            append("&limit=${request.limit.coerceIn(1, 1000)}")
            request.startTime?.let { append("&startTime=$it") }
            request.endTime?.let { append("&endTime=$it") }
        }
        return get(params).map { body ->
            val arr = JSONArray(body)
            val candles = (0 until arr.length()).map { i ->
                val c = arr.getJSONArray(i)
                Candle(
                    openTime            = c.getLong(0),
                    open                = c.getDouble(1),
                    high                = c.getDouble(2),
                    low                 = c.getDouble(3),
                    close               = c.getDouble(4),
                    volume              = c.getDouble(5),
                    closeTime           = c.getLong(6),
                    quoteVolume         = c.getDouble(7),
                    trades              = c.getInt(8),
                    takerBuyVolume      = c.getDouble(9),
                    takerBuyQuoteVolume = c.getDouble(10),
                    isClosed            = true
                )
            }
            CandleSeriesData(
                symbol   = request.symbol.uppercase(),
                interval = request.interval.code,
                candles  = candles
            )
        }
    }

    // ─── 2. Single ticker — 24h price statistics ─────────────────────────────

    suspend fun getTicker24h(symbol: String): Result<TickerData> {
        return get("/ticker/24hr?symbol=${symbol.uppercase()}").map { body ->
            parseTicker(JSONObject(body))
        }
    }

    // ─── 3. Multiple tickers ─────────────────────────────────────────────────
    // NOTE: Binance /ticker/24hr?symbols= requires raw JSON array in query param
    // URL-encoding the brackets causes a 400 error. Use raw string.

    suspend fun getMultipleTickers(symbols: List<String>): Result<List<TickerData>> {
        if (symbols.isEmpty()) return Result.success(emptyList())
        // Batch in groups of 20 to avoid URL length limits
        val chunks = symbols.chunked(20)
        val allTickers = mutableListOf<TickerData>()
        for (chunk in chunks) {
            val arr = chunk.map { "\"${it.uppercase()}\"" }.joinToString(",", "[", "]")
            val result = get("/ticker/24hr?symbols=$arr").map { body ->
                val jsonArr = JSONArray(body)
                (0 until jsonArr.length()).map { i ->
                    val j = jsonArr.getJSONObject(i)
                    parseTicker(j)
                }
            }
            result.onSuccess { allTickers.addAll(it) }
            result.onFailure { return Result.failure(it) }
        }
        return Result.success(allTickers)
    }

    private fun parseTicker(j: JSONObject) = TickerData(
        symbol         = j.getString("symbol"),
        price          = j.optString("lastPrice","0").toDoubleOrNull() ?: 0.0,
        priceChange    = j.optString("priceChange","0").toDoubleOrNull() ?: 0.0,
        priceChangePct = j.optString("priceChangePercent","0").toDoubleOrNull() ?: 0.0,
        highPrice      = j.optString("highPrice","0").toDoubleOrNull() ?: 0.0,
        lowPrice       = j.optString("lowPrice","0").toDoubleOrNull() ?: 0.0,
        volume         = j.optString("volume","0").toDoubleOrNull() ?: 0.0,
        quoteVolume    = j.optString("quoteVolume","0").toDoubleOrNull() ?: 0.0,
        openPrice      = j.optString("openPrice","0").toDoubleOrNull() ?: 0.0
    )

    // ─── 4. Order book snapshot ───────────────────────────────────────────────

    suspend fun getOrderBookSnapshot(symbol: String, limit: Int = 20): Result<OrderBookData> {
        val safeLimit = when {
            limit <= 5 -> 5
            limit <= 10 -> 10
            limit <= 20 -> 20
            limit <= 50 -> 50
            limit <= 100 -> 100
            else -> 100
        }
        return get("/depth?symbol=${symbol.uppercase()}&limit=$safeLimit").map { body ->
            val j = JSONObject(body)

            fun parseEntries(arr: JSONArray): List<OrderBookEntry> =
                (0 until arr.length()).map { i ->
                    val entry = arr.getJSONArray(i)
                    OrderBookEntry(entry.getDouble(0), entry.getDouble(1))
                }

            val bids = parseEntries(j.getJSONArray("bids")).sortedByDescending { it.price }
            val asks = parseEntries(j.getJSONArray("asks")).sortedBy { it.price }

            OrderBookData(
                symbol       = symbol.uppercase(),
                bids         = bids,
                asks         = asks,
                lastUpdateId = j.getLong("lastUpdateId")
            )
        }
    }

    // ─── 5. Available intervals for a symbol ─────────────────────────────────
    //  Binance supports all intervals for all symbols.
    //  We return the full list with a ping to verify the symbol exists.

    suspend fun getAvailableIntervals(symbol: String): Result<List<CandleInterval>> {
        // Verify symbol exists by fetching a minimal kline request
        return get("/klines?symbol=${symbol.uppercase()}&interval=1d&limit=1").map {
            CandleInterval.values().toList()
        }
    }

    // ─── 6. Available USDT symbols — lightweight via /ticker/price ───────────
    // /exchangeInfo is 2MB+ and times out on mobile. /ticker/price returns all
    // trading pairs with just symbol+price, then we filter for USDT pairs.

    suspend fun getUsdtSymbols(): Result<List<String>> {
        return get("/ticker/price").map { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val sym = arr.getJSONObject(i).getString("symbol")
                if (sym.endsWith("USDT")) sym else null
            }.sorted()
        }
    }

    // ─── 7. Current price only ────────────────────────────────────────────────

    suspend fun getCurrentPrice(symbol: String): Result<Double> {
        return get("/ticker/price?symbol=${symbol.uppercase()}").map { body ->
            JSONObject(body).getDouble("price")
        }
    }

    // ─── 8. Server time / connectivity check ─────────────────────────────────

    suspend fun ping(): Result<Boolean> {
        return get("/ping").map { true }
    }

    suspend fun getServerTime(): Result<Long> {
        return get("/time").map { body ->
            JSONObject(body).getLong("serverTime")
        }
    }
}
