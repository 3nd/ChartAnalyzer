package com.chartanalyzer.app.models

// ─── Signal Types ─────────────────────────────────────────────────────────────

enum class SignalType {
    STRONG_BUY,
    BUY,
    NEUTRAL,
    SELL,
    STRONG_SELL
}

enum class SignalSource {
    RSI_DIVERGENCE,
    MACD_CROSS,
    BB_SQUEEZE_BREAK,
    VOLUME_SURGE,
    ORDER_BLOCK,
    FAIR_VALUE_GAP,
    LIQUIDITY_SWEEP,
    WYCKOFF_SPRING,
    WYCKOFF_UTAD,
    MULTI_CONFLUENCE,
    EMA_CROSS,
    STRUCTURE_BREAK,
    HARMONIC_PATTERN,
    SUPPORT_BOUNCE,
    RESISTANCE_REJECT,
    MOON_CYCLE,         // Lunar cycle phase signal (29.53-day synodic period)
    SHEMITAH_CYCLE,     // Biblical 7-year macro cycle enhancer (Torah / Cahn 2014)
    ML_COMPOSITE        // weighted composite of all signals
}

// ─── Prediction signal attached to a specific candle ─────────────────────────

data class PredictionSignal(
    val timestamp: Long,        // candle open time (seconds for TV Lightweight)
    val price: Double,          // price at signal
    val type: SignalType,
    val source: SignalSource,
    val confidence: Double,     // 0.0–1.0
    val buyStrength: Double,    // 0–100
    val sellStrength: Double,   // 0–100
    val targetPrice: Double? = null,
    val stopLoss: Double? = null,
    val riskReward: Double? = null,
    val notes: String = ""
)

// ─── Price zone (S/R, target, stop) ──────────────────────────────────────────

enum class PriceZoneType { SUPPORT, RESISTANCE, TARGET, STOP_LOSS, ENTRY }

data class PriceZone(
    val id: String,
    val price: Double,
    val type: PriceZoneType,
    val label: String,
    val color: String,          // CSS hex color for TradingView
    val lineWidth: Int = 1,
    val lineDash: Boolean = false
)

// ─── Candle state manager ─────────────────────────────────────────────────────

data class CandleState(
    val symbol: String,
    val interval: CandleInterval,
    val confirmedCandles: List<Candle> = emptyList(),   // closed candles only
    val liveCandle: Candle? = null,                      // current forming candle
    val lastSignal: PredictionSignal? = null,
    val allSignals: List<PredictionSignal> = emptyList(),
    val priceZones: List<PriceZone> = emptyList(),
    val buyStrengthSeries: List<Pair<Long, Double>> = emptyList(),   // (timestampSec, value 0-100)
    val sellStrengthSeries: List<Pair<Long, Double>> = emptyList(),
    val confidenceSeries: List<Pair<Long, Double>> = emptyList(),
    // ── Lunar cycle state ──────────────────────────────────────────────────────
    val lunarAge: Double = 0.0,           // days since last New Moon (0–29.53)
    val lunarIllumination: Double = 0.0,  // illumination fraction (0.0–1.0)
    val lunarPhase: LunarCalculator.MoonPhase = LunarCalculator.MoonPhase.NEW_MOON,
    val lunarScore: Double = 0.0,         // Moon_Score ∈ [-1, +1]
    val lunarReversalProximity: Double = 0.0,  // 1.0 = exact New/Full Moon
    val moonPhaseSeries: List<Pair<Long, Double>> = emptyList(),  // (timestampSec, phaseAngle 0–360)
    val moonIlluminationSeries: List<Pair<Long, Double>> = emptyList()  // (timestampSec, illum 0–100)
)

// ─── Multi-timeframe state ────────────────────────────────────────────────────

data class MultiTimeframeState(
    val symbol: String,
    val frames: Map<CandleInterval, CandleState> = emptyMap(),
    val activeInterval: CandleInterval = CandleInterval.ONE_HOUR
)

// ─── Prediction metadata for Info Box ────────────────────────────────────────

data class PredictionMeta(
    val signal: SignalType,
    val confidence: Double,
    val buyStrength: Double,
    val sellStrength: Double,
    val dominantSource: SignalSource,
    val targetPrice: Double?,
    val stopLoss: Double?,
    val riskReward: Double?,
    val timestamp: Long,
    val notes: String
)

// ─── Backtest result ──────────────────────────────────────────────────────────

data class BacktestResult(
    val signal: PredictionSignal,
    val entryPrice: Double,
    val exitPrice: Double?,
    val pnlPct: Double?,
    val wasCorrect: Boolean?,
    val barsHeld: Int
)

// ─── Chart config ─────────────────────────────────────────────────────────────

data class ChartConfig(
    val showVolume: Boolean = true,
    val showBuyStrength: Boolean = true,
    val showSellStrength: Boolean = true,
    val showConfidence: Boolean = true,
    val showSignalMarkers: Boolean = true,
    val showPriceZones: Boolean = true,
    val showCrosshair: Boolean = true,
    val colorBullCandle: String = "#26a69a",
    val colorBearCandle: String = "#ef5350",
    val colorBuySignal: String = "#00e676",
    val colorSellSignal: String = "#ff1744",
    val colorConfidence: String = "#7c4dff",
    val colorBuyStrength: String = "#00b0ff",
    val colorSellStrength: String = "#ff6d00"
)
