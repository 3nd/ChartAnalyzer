package com.chartanalyzer.app.api

import android.content.Context
import com.chartanalyzer.app.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * MLEnsembleEngine
 *
 * Orchestrates the full ML pipeline:
 *   1. Fetch historical data (3-12 months via Binance REST)
 *   2. Feature engineering (FeatureEngineer → 45 features per candle)
 *   3. Train GradientBoostingModel (XGBoost-style)
 *   4. Train LSTMModel (temporal sequence model)
 *   5. Combine predictions (weighted ensemble)
 *   6. Run backtest on hold-out set
 *   7. Serve real-time predictions via WebSocket data
 *
 * Disagreement logic:
 *   If |GB_buy_prob - LSTM_buy_prob| > disagreementThreshold → HOLD / low confidence
 *   If both agree → high confidence signal
 */
class MLEnsembleEngine(private val context: Context) {

    private val restService = BinanceRestService()
    private val featureEngineer = FeatureEngineer()
    private val gbModel = GradientBoostingModel()
    private val lstmModel = LSTMModel()

    private val _trainingProgress = MutableStateFlow(TrainingProgress())
    val trainingProgress: StateFlow<TrainingProgress> = _trainingProgress.asStateFlow()

    private val _latestPrediction = MutableStateFlow<EnsemblePrediction?>(null)
    val latestPrediction: StateFlow<EnsemblePrediction?> = _latestPrediction.asStateFlow()

    private var recentFeatures = mutableListOf<FeatureVector>()
    private var savedModelInfo: SavedModelInfo? = null
    private var config = EnsembleConfig()
    private var trainingJob: Job? = null

    // ─── Full training pipeline ────────────────────────────────────────────────

    fun train(
        symbol: String,
        interval: CandleInterval,
        ensembleConfig: EnsembleConfig = EnsembleConfig(),
        scope: CoroutineScope
    ) {
        trainingJob?.cancel()
        config = ensembleConfig
        trainingJob = scope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            try {
                // ── Step 1: Fetch historical data ─────────────────────────────
                updateProgress(TrainingProgress(
                    status = TrainingStatus.FETCHING_DATA,
                    symbol = symbol,
                    interval = interval.code
                ))

                // Determine how many bars to fetch based on training window config
                val gbBars  = ensembleConfig.gbConfig.trainingWindow.bars1h
                val lstmBars = ensembleConfig.lstmConfig.trainingWindow.bars1h
                val totalBars = maxOf(gbBars, lstmBars).coerceAtMost(1000)

                // Binance limit is 1000 per request — fetch in batches
                val allCandles = fetchAllCandles(symbol, interval, totalBars)
                    .also { if (it.isEmpty()) throw IllegalStateException("No candle data returned") }

                updateProgress(_trainingProgress.value.copy(totalCandles = allCandles.size))

                // ── Step 2: Feature engineering ───────────────────────────────
                updateProgress(_trainingProgress.value.copy(
                    status = TrainingStatus.PREPROCESSING))

                val gbCfg = ensembleConfig.gbConfig
                val lstmCfg = ensembleConfig.lstmConfig

                // Use the last N candles per model's window setting
                val gbCandles   = allCandles.takeLast(gbBars.coerceAtMost(allCandles.size))
                val lstmCandles = allCandles.takeLast(lstmBars.coerceAtMost(allCandles.size))

                val gbFeatures   = featureEngineer.buildFeatureMatrix(
                    gbCandles, lookahead = 3, addLabels = true)
                val lstmFeatures = featureEngineer.buildFeatureMatrix(
                    lstmCandles, lookahead = 3, addLabels = true)

                if (gbFeatures.size < 50 || lstmFeatures.size < 50) {
                    throw IllegalStateException("Insufficient data after feature engineering")
                }

                updateProgress(_trainingProgress.value.copy(
                    processedCandles = gbFeatures.size,
                    totalEpochs = gbCfg.nTrees + lstmCfg.epochs
                ))

                // ── Step 3: Train Gradient Boosting (only if enabled) ─────────
                if (ensembleConfig.useGradientBoosting) {
                    updateProgress(_trainingProgress.value.copy(
                        status = TrainingStatus.TRAINING_GB))
                    gbModel.train(gbFeatures, gbCfg) { epoch, loss, acc ->
                        updateProgress(_trainingProgress.value.copy(
                            currentEpoch = epoch,
                            totalEpochs  = gbCfg.nTrees,
                            trainLoss    = loss,
                            trainAccuracy = acc,
                            elapsedMs    = System.currentTimeMillis() - startTime
                        ))
                    }
                }

                // ── Step 4: Train LSTM (only if enabled) ─────────────────────
                if (ensembleConfig.useLSTM) {
                    updateProgress(_trainingProgress.value.copy(
                        status = TrainingStatus.TRAINING_LSTM, currentEpoch = 0))
                    lstmModel.train(lstmFeatures, lstmCfg) { epoch, tLoss, vLoss, acc ->
                        updateProgress(_trainingProgress.value.copy(
                            currentEpoch = epoch,
                            totalEpochs  = lstmCfg.epochs,
                            trainLoss    = tLoss,
                            valLoss      = vLoss,
                            valAccuracy  = acc,
                            elapsedMs    = System.currentTimeMillis() - startTime
                        ))
                    }
                }

                // ── Step 5: Backtest on hold-out ──────────────────────────────
                updateProgress(_trainingProgress.value.copy(status = TrainingStatus.BACKTESTING))

                val backtestMetrics = runBacktest(gbFeatures, lstmFeatures, ensembleConfig)

                // ── Step 6: Store recent features for real-time inference ─────
                updateProgress(_trainingProgress.value.copy(status = TrainingStatus.COMBINING))

                recentFeatures = featureEngineer.buildFeatureMatrix(
                    allCandles.takeLast(200), lookahead = 0, addLabels = false
                ).toMutableList()

                // ── Complete ──────────────────────────────────────────────────
                savedModelInfo = SavedModelInfo(
                    symbol = symbol,
                    interval = interval.code,
                    trainedAt = System.currentTimeMillis(),
                    trainingWindow = maxOf(gbCfg.trainingWindow, lstmCfg.trainingWindow,
                        compareBy { it.months }),
                    gbAccuracy = backtestMetrics.winRate,
                    lstmAccuracy = backtestMetrics.winRate,
                    ensembleAccuracy = backtestMetrics.winRate,
                    totalSamples = gbFeatures.size,
                    featureCount = featureEngineer.featureCount
                )

                updateProgress(_trainingProgress.value.copy(
                    status = TrainingStatus.READY,
                    gbAccuracy = backtestMetrics.winRate,
                    ensembleAccuracy = backtestMetrics.winRate,
                    backtestWinRate = backtestMetrics.winRate,
                    backtestPnl = backtestMetrics.totalPnlPct,
                    elapsedMs = System.currentTimeMillis() - startTime
                ))

                // Fire first prediction immediately
                inferLatest()

            } catch (e: CancellationException) {
                updateProgress(TrainingProgress(status = TrainingStatus.IDLE))
            } catch (e: Exception) {
                updateProgress(_trainingProgress.value.copy(
                    status = TrainingStatus.FAILED,
                    errorMessage = e.message ?: "Unknown error"
                ))
            }
        }
    }

    // ─── Real-time inference (called on each closed candle) ───────────────────

    fun onNewCandle(@Suppress("UNUSED_PARAMETER") candle: Candle, allCandles: List<Candle>) {
        if (!gbModel.trained && !lstmModel.trained) return
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                // Update rolling feature window
                val newFeatures = featureEngineer.buildFeatureMatrix(
                    allCandles.takeLast(220), lookahead = 0, addLabels = false
                )
                if (newFeatures.isNotEmpty()) {
                    recentFeatures = newFeatures.toMutableList()
                }
                inferLatest()
            } catch (_: Exception) { }
        }
    }

    private suspend fun inferLatest() = withContext(Dispatchers.Default) {
        if (recentFeatures.isEmpty()) return@withContext
        val lastFv = recentFeatures.last()

        val useGB   = config.useGradientBoosting && gbModel.trained
        val useLSTM = config.useLSTM && lstmModel.trained

        if (!useGB && !useLSTM) return@withContext

        // Apply cycle enhancer modifiers to features if enabled
        val fv = applyEnhancers(lastFv)

        val gbPred   = if (useGB)   gbModel.predict(fv)   else neutralPrediction(fv)
        val lstmPred = if (useLSTM) lstmModel.predict(recentFeatures.map { applyEnhancers(it) })
                       else neutralPrediction(fv)

        // Adjust weights when only one model is active
        val effectiveCfg = when {
            useGB && !useLSTM -> config.copy(gbWeight = 1.0, lstmWeight = 0.0)
            !useGB && useLSTM -> config.copy(gbWeight = 0.0, lstmWeight = 1.0)
            else              -> config
        }

        val ensemble = combine(gbPred, lstmPred, effectiveCfg)
        _latestPrediction.value = ensemble
    }

    private fun applyEnhancers(fv: FeatureVector): FeatureVector {
        // Zero out cycle features if the enhancer is disabled
        return fv.copy(
            lunarAge          = if (config.useMoonCycleEnhancer) fv.lunarAge           else 0.0,
            lunarIllumination = if (config.useMoonCycleEnhancer) fv.lunarIllumination  else 0.0,
            lunarMomentum     = if (config.useMoonCycleEnhancer) fv.lunarMomentum      else 0.0,
            lunarReversalProx = if (config.useMoonCycleEnhancer) fv.lunarReversalProx  else 0.0,
            isNewMoonWindow   = if (config.useMoonCycleEnhancer) fv.isNewMoonWindow    else 0.0,
            isFullMoonWindow  = if (config.useMoonCycleEnhancer) fv.isFullMoonWindow   else 0.0,
            shemitahScore         = if (config.useShemitahEnhancer) fv.shemitahScore         else 0.0,
            shemitahYearNorm      = if (config.useShemitahEnhancer) fv.shemitahYearNorm      else 0.0,
            shemitahBearPressure  = if (config.useShemitahEnhancer) fv.shemitahBearPressure  else 0.0,
            shemitahBullRecovery  = if (config.useShemitahEnhancer) fv.shemitahBullRecovery  else 0.0,
            elulProximity         = if (config.useShemitahEnhancer) fv.elulProximity         else 0.0
        )
    }

    private fun neutralPrediction(fv: FeatureVector) = ModelPrediction(
        buyProbability  = 0.33,
        sellProbability = 0.33,
        holdProbability = 0.34,
        modelType  = ModelType.ENSEMBLE,
        timestamp  = fv.timestamp,
        price      = fv.price,
        topFeatures = emptyList()
    )

    // ─── Ensemble combiner ────────────────────────────────────────────────────

    fun combine(
        gbPred: ModelPrediction,
        lstmPred: ModelPrediction,
        cfg: EnsembleConfig = config
    ): EnsemblePrediction {
        val w1 = cfg.gbWeight; val w2 = cfg.lstmWeight
        val norm = w1 + w2

        // Weighted average probabilities
        val buyProb  = (gbPred.buyProbability  * w1 + lstmPred.buyProbability  * w2) / norm
        val sellProb = (gbPred.sellProbability * w1 + lstmPred.sellProbability * w2) / norm
        val holdProb = (gbPred.holdProbability * w1 + lstmPred.holdProbability * w2) / norm

        // Agreement metric
        val agreement = 1.0 - abs(gbPred.buyProbability - lstmPred.buyProbability)
            .coerceIn(0.0, 1.0)

        // Disagreement → override to NEUTRAL/HOLD
        val disagree = abs(gbPred.buyProbability - lstmPred.buyProbability) > cfg.disagreementThreshold

        val signalType = if (disagree) {
            SignalType.NEUTRAL
        } else {
            when {
                buyProb >= 0.65 && buyProb > sellProb  -> SignalType.STRONG_BUY
                buyProb >= 0.55 && buyProb > sellProb  -> SignalType.BUY
                sellProb >= 0.65 && sellProb > buyProb -> SignalType.STRONG_SELL
                sellProb >= 0.55 && sellProb > buyProb -> SignalType.SELL
                else -> SignalType.NEUTRAL
            }
        }

        val confidence = if (disagree) agreement * 0.4
        else (agreement * maxOf(buyProb, sellProb, holdProb)).coerceIn(0.0, 1.0)

        val notes = buildString {
            append("GB: buy=${"%.1f".format(gbPred.buyProbability * 100)}% ")
            append("sell=${"%.1f".format(gbPred.sellProbability * 100)}% | ")
            append("LSTM: buy=${"%.1f".format(lstmPred.buyProbability * 100)}% ")
            append("sell=${"%.1f".format(lstmPred.sellProbability * 100)}% | ")
            append("Agreement: ${"%.0f".format(agreement * 100)}%")
            if (disagree) append(" ⚠️ DISAGREE → HOLD")
        }

        return EnsemblePrediction(
            signal = signalType,
            buyProbability  = buyProb  * 100.0,
            sellProbability = sellProb * 100.0,
            holdProbability = holdProb * 100.0,
            confidence = confidence,
            gbPrediction   = gbPred,
            lstmPrediction = lstmPred,
            agreement = agreement,
            timestamp = gbPred.timestamp,
            price = gbPred.price,
            topFeatures = gbPred.topFeatures,
            notes = notes
        )
    }

    // ─── Historical fetch (batch up to Binance limit) ─────────────────────────

    private suspend fun fetchAllCandles(
        symbol: String,
        interval: CandleInterval,
        targetBars: Int
    ): List<Candle> = withContext(Dispatchers.IO) {
        val allCandles = mutableListOf<Candle>()
        val batchSize = 1000
        var endTime: Long? = null
        var remaining = targetBars

        while (remaining > 0) {
            val batchLimit = minOf(batchSize, remaining)
            val result = restService.getHistoricalCandles(
                HistoricalDataRequest(symbol, interval, batchLimit, endTime = endTime)
            ).getOrNull() ?: break

            val batch = result.candles
            if (batch.isEmpty()) break

            allCandles.addAll(0, batch)  // prepend (ascending time)
            endTime = batch.first().openTime - 1  // fetch earlier data
            remaining -= batch.size

            if (batch.size < batchLimit) break  // no more data
        }
        allCandles.distinctBy { it.openTime }.sortedBy { it.openTime }
    }

    // ─── Backtest on hold-out (last 20%) ─────────────────────────────────────

    private fun runBacktest(
        gbFeatures: List<FeatureVector>,
        lstmFeatures: List<FeatureVector>,
        cfg: EnsembleConfig
    ): BacktestMetrics {
        val splitIdx = (gbFeatures.size * 0.8).toInt()
        val holdout = gbFeatures.drop(splitIdx)
        val lstmHoldout = lstmFeatures.drop((lstmFeatures.size * 0.8).toInt())

        val results = mutableListOf<BacktestTradeResult>()
        var wins = 0; var losses = 0
        var totalWinPnl = 0.0; var totalLossPnl = 0.0

        for ((i, fv) in holdout.withIndex()) {
            if (fv.label == null) continue
            val gbPred = gbModel.predict(fv)
            val lstmSeq = lstmHoldout.take(i + 1)
            val lstmPred = lstmModel.predict(lstmSeq.takeLast(cfg.lstmConfig.sequenceLength))
            val ensemble = combine(gbPred, lstmPred, cfg)

            if (ensemble.signal == SignalType.NEUTRAL) continue
            if (ensemble.confidence < cfg.minConfidenceForSignal) continue

            val isBuy = ensemble.signal == SignalType.BUY || ensemble.signal == SignalType.STRONG_BUY
            val actualLabel = fv.label
            val wasCorrect = (isBuy && actualLabel == 1) || (!isBuy && actualLabel == -1)
            val pnl = if (isBuy) ((fv.price * 1.005 - fv.price) / fv.price) * 100.0
            else ((fv.price - fv.price * 0.995) / fv.price) * 100.0
            val actualPnl = if (wasCorrect) pnl else -pnl * 0.5

            if (actualPnl > 0) { wins++; totalWinPnl += actualPnl }
            else { losses++; totalLossPnl += abs(actualPnl) }

            results.add(BacktestTradeResult(
                entryTime = fv.timestamp, exitTime = fv.timestamp + 3 * 3600_000L,
                signal = ensemble.signal, entryPrice = fv.price,
                exitPrice = if (wasCorrect) fv.price * 1.005 else fv.price * 0.998,
                pnlPct = actualPnl, confidence = ensemble.confidence,
                gbProb = if (isBuy) gbPred.buyProbability else gbPred.sellProbability,
                lstmProb = if (isBuy) lstmPred.buyProbability else lstmPred.sellProbability,
                wasCorrect = wasCorrect
            ))
        }

        val total = (wins + losses).coerceAtLeast(1)
        val winRate = wins.toDouble() / total
        val profitFactor = if (totalLossPnl > 0) totalWinPnl / totalLossPnl else 1.0
        val totalPnl = totalWinPnl - totalLossPnl
        val avgWin = if (wins > 0) totalWinPnl / wins else 0.0
        val avgLoss = if (losses > 0) totalLossPnl / losses else 0.0

        return BacktestMetrics(
            totalTrades = total, winningTrades = wins, losingTrades = losses,
            winRate = winRate, avgWinPct = avgWin, avgLossPct = avgLoss,
            profitFactor = profitFactor, maxDrawdownPct = 0.0,
            totalPnlPct = totalPnl,
            sharpeRatio = if (totalPnl > 0) totalPnl / sqrt(total.toDouble()) else 0.0,
            results = results.takeLast(50)
        )
    }

    private fun updateProgress(p: TrainingProgress) { _trainingProgress.value = p }

    val isModelTrained get() = gbModel.trained && lstmModel.trained
    val modelInfo get() = savedModelInfo

    fun cancelTraining() { trainingJob?.cancel() }

    /** Update runtime config — model toggles, enhancer flags — without retraining */
    fun updateConfig(newConfig: EnsembleConfig) { config = newConfig }

    fun getFeatureImportance(): List<Pair<String, Double>> = gbModel.getTopFeatures(15)
}
