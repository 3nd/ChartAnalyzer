package com.chartanalyzer.app.models

// ─── Training configuration ───────────────────────────────────────────────────

enum class TrainingWindow(val label: String, val months: Int, val bars1h: Int) {
    THREE_MONTHS("3 Months",   3,  2160),
    SIX_MONTHS  ("6 Months",   6,  4320),
    NINE_MONTHS ("9 Months",   9,  6480),
    TWELVE_MONTHS("12 Months", 12, 8760)
}

enum class ModelType { GRADIENT_BOOSTING, LSTM, ENSEMBLE }

enum class TrainingStatus {
    IDLE, FETCHING_DATA, PREPROCESSING, TRAINING_GB, TRAINING_LSTM,
    COMBINING, BACKTESTING, READY, FAILED
}

data class GBConfig(
    val nTrees: Int = 100,
    val maxDepth: Int = 5,
    val learningRate: Double = 0.05,
    val subsampleRatio: Double = 0.8,
    val featureSubsampleRatio: Double = 0.8,
    val minSamplesLeaf: Int = 10,
    val l2Regularization: Double = 1.0,
    val useFrameworkFeatures: Boolean = true,   // incorporate 27 framework features
    val trainingWindow: TrainingWindow = TrainingWindow.SIX_MONTHS
)

data class LSTMConfig(
    val sequenceLength: Int = 60,               // bars lookback
    val hiddenUnits: Int = 64,
    val numLayers: Int = 2,
    val dropoutRate: Double = 0.2,
    val epochs: Int = 50,
    val batchSize: Int = 32,
    val learningRate: Double = 0.001,
    val useFrameworkFeatures: Boolean = true,
    val trainingWindow: TrainingWindow = TrainingWindow.TWELVE_MONTHS
)

data class EnsembleConfig(
    val gbConfig: GBConfig = GBConfig(),
    val lstmConfig: LSTMConfig = LSTMConfig(),
    val gbWeight: Double = 0.55,
    val lstmWeight: Double = 0.45,
    val disagreementThreshold: Double = 0.25,
    val minConfidenceForSignal: Double = 0.60,
    // Model selection toggles (v2.0)
    val useGradientBoosting: Boolean = true,
    val useLSTM: Boolean = true,
    // Cycle enhancer toggles (v2.0)
    val useMoonCycleEnhancer: Boolean = true,
    val useShemitahEnhancer: Boolean = true
)

// ─── Feature vector (one row per candle) ─────────────────────────────────────

data class FeatureVector(
    val timestamp: Long,
    val price: Double,
    // Raw OHLCV
    val open: Double, val high: Double, val low: Double,
    val close: Double, val volume: Double,
    // Price change
    val returnPct: Double,        // (close - prev_close) / prev_close
    val highLowRange: Double,     // (high-low)/close
    val bodyRatio: Double,        // |close-open| / (high-low)
    val upperWickRatio: Double,
    val lowerWickRatio: Double,
    // Momentum
    val rsi14: Double,
    val rsi7: Double,
    val stochK14: Double,
    val stochD14: Double,
    val macdLine: Double,
    val macdSignal: Double,
    val macdHist: Double,
    // Trend
    val ema8: Double,
    val ema21: Double,
    val ema50: Double,
    val ema200: Double,
    val ema8_21_cross: Double,    // sign of ema8 - ema21
    val priceAboveEma50: Double,  // 1 or 0
    val priceAboveEma200: Double,
    // Volatility
    val atr14: Double,
    val bbWidth: Double,          // (upperBB - lowerBB) / midBB
    val bbPct: Double,            // (close - lowerBB) / (upperBB - lowerBB)
    val chopIndex: Double,        // 0–100
    val bbInSqueeze: Double,      // 1 if BB inside KC
    // Volume
    val volumeRatio: Double,      // volume / SMA_vol_20
    val vsaSpreadRatio: Double,
    val obv: Double,              // normalized OBV
    val cvdProxy: Double,         // taker_buy_vol / total_vol
    // Multi-timeframe proxies
    val trend4h: Double,          // sign of 4h ema21-ema50 (approximated from 1h)
    val trendDay: Double,
    // ── 27-Framework features ─────────────────────────────────────────────────
    val vsaSignal: Double,
    val wyckoffPhaseScore: Double,
    val confluenceScore: Double,
    val hiddenDivergenceScore: Double,
    val elliottWaveScore: Double,
    val fibRetracementZone: Double,  // proximity to nearest fib level (-1 to 1)
    val harmonicPatternScore: Double,
    val orderBlockProximity: Double,
    val fvgFillProb: Double,
    val liquidityClusterScore: Double,
    val structureBreakScore: Double,
    val candlestickPatternScore: Double,
    val sweepLiquidityScore: Double,
    val supplyDemandScore: Double,
    val chopIndexNorm: Double,       // normalized chop 0-1
    // ── Lunar / Moon Cycle features (v1.7) ─────────────────────────────────────
    val lunarAge: Double,            // days since last New Moon, normalized 0–1  (0 = New, 0.5 = Full)
    val lunarIllumination: Double,   // illumination fraction 0.0–1.0
    val lunarMomentum: Double,       // sin(phaseAngle) ∈ [-1, +1] (+ = waxing, - = waning)
    val lunarReversalProx: Double,   // reversal proximity 0–1 (1 = exactly New/Full Moon)
    val isNewMoonWindow: Double,     // 1.0 if within ±1.85 days of New Moon, else 0.0
    val isFullMoonWindow: Double,    // 1.0 if within ±1.85 days of Full Moon, else 0.0
    // ── Shemitah / Biblical 7-Year Macro Cycle features (v1.9) ────────────────
    val shemitahScore: Double,       // composite Shemitah score ∈ [-1,+1]
    val shemitahYearNorm: Double,    // year-in-cycle 1-7, normalised to 0-1
    val shemitahBearPressure: Double,// 0-1: active bearish macro pressure
    val shemitahBullRecovery: Double,// 0-1: active post-Shemitah recovery signal
    val elulProximity: Double,       // 0-1: proximity to Elul 29 (Sep 13 ±15d)
    // Label (for training) - null at inference time
    val label: Int? = null          // 1=buy, 0=hold, -1=sell
)

// ─── Model output ─────────────────────────────────────────────────────────────

data class ModelPrediction(
    val buyProbability: Double,    // 0.0–1.0
    val sellProbability: Double,   // 0.0–1.0
    val holdProbability: Double,
    val modelType: ModelType,
    val timestamp: Long,
    val price: Double,
    val topFeatures: List<Pair<String, Double>> = emptyList(),  // feature importance (GB only)
    val inferenceMs: Long = 0
)

data class EnsemblePrediction(
    val signal: SignalType,
    val buyProbability: Double,     // 0–100 (percent)
    val sellProbability: Double,    // 0–100
    val holdProbability: Double,
    val confidence: Double,         // 0.0–1.0
    val gbPrediction: ModelPrediction,
    val lstmPrediction: ModelPrediction,
    val agreement: Double,          // 0.0–1.0 (1=full agreement)
    val timestamp: Long,
    val price: Double,
    val topFeatures: List<Pair<String, Double>> = emptyList(),
    val notes: String = ""
)

// ─── Training progress ────────────────────────────────────────────────────────

data class TrainingProgress(
    val status: TrainingStatus = TrainingStatus.IDLE,
    val symbol: String = "",
    val interval: String = "",
    val totalCandles: Int = 0,
    val processedCandles: Int = 0,
    val currentEpoch: Int = 0,
    val totalEpochs: Int = 0,
    val trainLoss: Double = 0.0,
    val valLoss: Double = 0.0,
    val trainAccuracy: Double = 0.0,
    val valAccuracy: Double = 0.0,
    val gbAccuracy: Double = 0.0,
    val lstmAccuracy: Double = 0.0,
    val ensembleAccuracy: Double = 0.0,
    val backtestWinRate: Double = 0.0,
    val backtestPnl: Double = 0.0,
    val errorMessage: String = "",
    val elapsedMs: Long = 0
) {
    val progressPct: Int get() = when (status) {
        TrainingStatus.IDLE          -> 0
        TrainingStatus.FETCHING_DATA -> 5
        TrainingStatus.PREPROCESSING -> 15
        TrainingStatus.TRAINING_GB   -> 15 + ((currentEpoch.toDouble() / totalEpochs.coerceAtLeast(1)) * 30).toInt()
        TrainingStatus.TRAINING_LSTM -> 45 + ((currentEpoch.toDouble() / totalEpochs.coerceAtLeast(1)) * 35).toInt()
        TrainingStatus.COMBINING     -> 82
        TrainingStatus.BACKTESTING   -> 90
        TrainingStatus.READY         -> 100
        TrainingStatus.FAILED        -> 0
    }
}

// ─── Saved model metadata ─────────────────────────────────────────────────────

data class SavedModelInfo(
    val symbol: String,
    val interval: String,
    val trainedAt: Long,
    val trainingWindow: TrainingWindow,
    val gbAccuracy: Double,
    val lstmAccuracy: Double,
    val ensembleAccuracy: Double,
    val totalSamples: Int,
    val featureCount: Int
)

// ─── Backtest metrics ─────────────────────────────────────────────────────────

data class BacktestMetrics(
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val avgWinPct: Double,
    val avgLossPct: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val totalPnlPct: Double,
    val sharpeRatio: Double,
    val results: List<BacktestTradeResult>
)

data class BacktestTradeResult(
    val entryTime: Long,
    val exitTime: Long,
    val signal: SignalType,
    val entryPrice: Double,
    val exitPrice: Double,
    val pnlPct: Double,
    val confidence: Double,
    val gbProb: Double,
    val lstmProb: Double,
    val wasCorrect: Boolean
)
