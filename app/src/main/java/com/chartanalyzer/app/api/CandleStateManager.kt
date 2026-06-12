package com.chartanalyzer.app.api

import com.chartanalyzer.app.models.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * CandleStateManager
 *
 * Lightweight state manager that:
 * 1. Tracks the latest candlestick per symbol+interval
 * 2. Only emits a new CandleState when a candle is fully CLOSED
 * 3. Aggregates incoming tick/trade data into proper OHLCV per timeframe
 * 4. Runs the signal pipeline on every closed candle
 * 5. Maintains rolling price zones (S/R levels from swing points)
 */
class CandleStateManager {

    // Per symbol+interval state
    private val stateMap = ConcurrentHashMap<String, CandleState>()
    private val signalEngine = SignalEngine()

    // Flows emitting updated state on candle close
    private val _closedCandleFlow = MutableSharedFlow<CandleState>(extraBufferCapacity = 64)
    val closedCandleFlow: SharedFlow<CandleState> = _closedCandleFlow.asSharedFlow()

    // Emits on every live tick (for real-time price update, not chart redraw)
    private val _liveCandleFlow = MutableSharedFlow<CandleState>(extraBufferCapacity = 256)
    val liveCandleFlow: SharedFlow<CandleState> = _liveCandleFlow.asSharedFlow()

    private fun key(symbol: String, interval: CandleInterval) = "${symbol}_${interval.code}"

    // ─── Initialize with historical candles ───────────────────────────────────

    fun initWithHistory(symbol: String, interval: CandleInterval, candles: List<Candle>) {
        val k = key(symbol, interval)
        val closedCandles = candles.filter { it.isClosed }
        val zones = computePriceZones(closedCandles)
        val signals = replaySignals(closedCandles)
        val buyStrength = signals.map { Pair(it.timestamp / 1000, it.buyStrength) }
        val sellStrength = signals.map { Pair(it.timestamp / 1000, it.sellStrength) }
        val confidence = signals.map { Pair(it.timestamp / 1000, it.confidence * 100.0) }

        // Compute lunar data for all historical candles
        val lastCandle = closedCandles.lastOrNull()
        val lunarAgeInit = lastCandle?.let { LunarCalculator.lunarAge(it.openTime) } ?: 0.0
        val moonPhaseSeries = closedCandles.map { c ->
            val age = LunarCalculator.lunarAge(c.openTime)
            Pair(c.openTime / 1000L, LunarCalculator.phaseAngle(age))
        }
        val moonIllumSeries = closedCandles.map { c ->
            val age = LunarCalculator.lunarAge(c.openTime)
            Pair(c.openTime / 1000L, LunarCalculator.illumination(age) * 100.0)
        }

        stateMap[k] = CandleState(
            symbol = symbol,
            interval = interval,
            confirmedCandles = closedCandles,
            liveCandle = null,
            allSignals = signals,
            lastSignal = signals.lastOrNull(),
            priceZones = zones,
            buyStrengthSeries = buyStrength,
            sellStrengthSeries = sellStrength,
            confidenceSeries = confidence,
            lunarAge = lunarAgeInit,
            lunarIllumination = LunarCalculator.illumination(lunarAgeInit),
            lunarPhase = LunarCalculator.getPhase(lunarAgeInit),
            lunarScore = LunarCalculator.moonScore(lunarAgeInit),
            lunarReversalProximity = LunarCalculator.reversalProximity(lunarAgeInit),
            moonPhaseSeries = moonPhaseSeries,
            moonIlluminationSeries = moonIllumSeries
        )
    }

    // ─── Process incoming WebSocket candle update ─────────────────────────────

    suspend fun processCandle(symbol: String, interval: CandleInterval, candle: Candle) {
        val k = key(symbol, interval)
        val current = stateMap[k] ?: CandleState(symbol, interval)

        if (!candle.isClosed) {
            // Live tick — update live candle display only (no signal recalculation)
            val updated = current.copy(liveCandle = candle)
            stateMap[k] = updated
            _liveCandleFlow.emit(updated)
            return
        }

        // Candle CLOSED — only update chart and run signal pipeline when confirmed
        val lastConfirmed = current.confirmedCandles.lastOrNull()
        if (lastConfirmed?.openTime == candle.openTime) {
            // Replace last candle (update, not append)
            val updatedCandles = current.confirmedCandles.dropLast(1) + candle
            processClosedCandles(k, current, updatedCandles, symbol, interval, candle)
        } else if ((lastConfirmed?.openTime ?: 0L) < candle.openTime) {
            // New candle appended
            val updatedCandles = (current.confirmedCandles + candle).takeLast(500)
            processClosedCandles(k, current, updatedCandles, symbol, interval, candle)
        }
    }

    private suspend fun processClosedCandles(
        k: String,
        current: CandleState,
        candles: List<Candle>,
        symbol: String,
        interval: CandleInterval,
        newCandle: Candle
    ) {
        // Run signal engine on the new closed candle
        val signal = signalEngine.evaluate(candles, interval)
        val allSignals = (current.allSignals + signal).takeLast(200)

        // Update rolling series
        val buyStrength = allSignals.map { Pair(it.timestamp / 1000L, it.buyStrength) }
        val sellStrength = allSignals.map { Pair(it.timestamp / 1000L, it.sellStrength) }
        val confidence = allSignals.map { Pair(it.timestamp / 1000L, it.confidence * 100.0) }

        // Recompute zones every 10 candles
        val zones = if (candles.size % 10 == 0) computePriceZones(candles)
        else current.priceZones

        // ── Lunar cycle computation ────────────────────────────────────────────
        val lunarAgeNow    = LunarCalculator.lunarAge(newCandle.openTime)
        val lunarIllumNow  = LunarCalculator.illumination(lunarAgeNow)
        val lunarPhaseNow  = LunarCalculator.getPhase(lunarAgeNow)
        val lunarScoreNow  = LunarCalculator.moonScore(lunarAgeNow)
        val lunarRevProx   = LunarCalculator.reversalProximity(lunarAgeNow)

        // Build lunar phase angle series from all confirmed candles
        val moonPhaseSeries = candles.map { c ->
            val age = LunarCalculator.lunarAge(c.openTime)
            Pair(c.openTime / 1000L, LunarCalculator.phaseAngle(age))
        }
        val moonIllumSeries = candles.map { c ->
            val age = LunarCalculator.lunarAge(c.openTime)
            Pair(c.openTime / 1000L, LunarCalculator.illumination(age) * 100.0)
        }

        val updated = current.copy(
            confirmedCandles = candles,
            liveCandle = null,
            lastSignal = signal,
            allSignals = allSignals,
            priceZones = zones,
            buyStrengthSeries = buyStrength,
            sellStrengthSeries = sellStrength,
            confidenceSeries = confidence,
            lunarAge = lunarAgeNow,
            lunarIllumination = lunarIllumNow,
            lunarPhase = lunarPhaseNow,
            lunarScore = lunarScoreNow,
            lunarReversalProximity = lunarRevProx,
            moonPhaseSeries = moonPhaseSeries,
            moonIlluminationSeries = moonIllumSeries
        )
        stateMap[k] = updated
        _closedCandleFlow.emit(updated)
    }

    // ─── Get current state ────────────────────────────────────────────────────

    fun getState(symbol: String, interval: CandleInterval): CandleState? =
        stateMap[key(symbol, interval)]

    fun getAllStates(symbol: String): Map<CandleInterval, CandleState> =
        stateMap.entries
            .filter { it.key.startsWith(symbol) }
            .associate { CandleInterval.fromCode(it.key.substringAfter("_")) to it.value }

    // ─── Price zone computation (swing point S/R) ─────────────────────────────

    private fun computePriceZones(candles: List<Candle>): List<PriceZone> {
        if (candles.size < 10) return emptyList()
        val zones = mutableListOf<PriceZone>()
        val n = 5  // swing detection window

        // Find swing highs (resistance) and swing lows (support)
        for (i in n until candles.size - n) {
            val c = candles[i]
            val isSwingHigh = (i - n until i).all { candles[it].high <= c.high } &&
                              (i + 1..i + n).all { candles[it].high <= c.high }
            val isSwingLow  = (i - n until i).all { candles[it].low  >= c.low  } &&
                              (i + 1..i + n).all { candles[it].low  >= c.low  }

            if (isSwingHigh) {
                zones.add(PriceZone(
                    id = "R_${c.openTime}",
                    price = c.high,
                    type = PriceZoneType.RESISTANCE,
                    label = "R ${formatPrice(c.high)}",
                    color = "#ef5350",
                    lineWidth = 1,
                    lineDash = false
                ))
            }
            if (isSwingLow) {
                zones.add(PriceZone(
                    id = "S_${c.openTime}",
                    price = c.low,
                    type = PriceZoneType.SUPPORT,
                    label = "S ${formatPrice(c.low)}",
                    color = "#26a69a",
                    lineWidth = 1,
                    lineDash = false
                ))
            }
        }

        // Keep only the most recent 8 zones, deduplicate by proximity (within 0.3%)
        val deduplicated = zones.reversed().distinctBy { z ->
            (z.price / (z.price * 0.003)).toLong()
        }.take(8)

        // Add current last-close as potential entry zone
        candles.lastOrNull()?.let { last ->
            deduplicated.toMutableList().also {
                it.add(PriceZone(
                    id = "LAST_CLOSE",
                    price = last.close,
                    type = PriceZoneType.ENTRY,
                    label = "Last Close",
                    color = "#ffd600",
                    lineWidth = 1,
                    lineDash = true
                ))
            }
        }

        return deduplicated
    }

    // ─── Replay signals for historical candles ────────────────────────────────

    private fun replaySignals(candles: List<Candle>): List<PredictionSignal> {
        if (candles.size < 20) return emptyList()
        val signals = mutableListOf<PredictionSignal>()
        // Process windows of candles to generate historical signals
        for (i in 20 until candles.size) {
            val window = candles.subList(0, i + 1)
            val signal = signalEngine.evaluate(window, CandleInterval.ONE_HOUR)
            if (signal.type != SignalType.NEUTRAL || signals.isEmpty()) {
                signals.add(signal)
            }
        }
        return signals.takeLast(100)
    }

    private fun formatPrice(p: Double): String = when {
        p >= 1000 -> "%.0f".format(p)
        p >= 1    -> "%.2f".format(p)
        else      -> "%.4f".format(p)
    }

    fun clear(symbol: String, interval: CandleInterval) {
        stateMap.remove(key(symbol, interval))
    }

    fun clearAll() = stateMap.clear()
}
