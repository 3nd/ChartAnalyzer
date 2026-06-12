package com.chartanalyzer.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chartanalyzer.app.api.MLEnsembleEngine
import com.chartanalyzer.app.models.*
import com.chartanalyzer.app.utils.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MLUiState(
    val symbol: String = "BTCUSDT",
    val interval: CandleInterval = CandleInterval.ONE_HOUR,
    // Configuration
    val ensembleConfig: EnsembleConfig = EnsembleConfig(),
    val showGbConfig: Boolean = false,
    val showLstmConfig: Boolean = false,
    // Training
    val progress: TrainingProgress = TrainingProgress(),
    val modelInfo: SavedModelInfo? = null,
    // Live prediction
    val latestPrediction: EnsemblePrediction? = null,
    val predictionHistory: List<EnsemblePrediction> = emptyList(),
    // Feature importance
    val featureImportance: List<Pair<String, Double>> = emptyList(),
    // Backtest
    val showBacktest: Boolean = false,
    // Tabs: 0=Train, 1=Predictions, 2=Features, 3=Backtest
    val selectedTab: Int = 0,
    // Alerts
    val alertHistory: List<SignalAlert> = emptyList(),
    val alertConfig: AlertConfig = AlertConfig(),
    // Portfolio
    val openPositions: List<Position> = emptyList(),
    val closedPositions: List<Position> = emptyList(),
    val portfolioSummary: PortfolioSummary? = null,
    // Saved models on disk
    val savedModels: List<SavedModelInfo> = emptyList()
)

class MLViewModel(application: Application) : AndroidViewModel(application) {

    val engine            = MLEnsembleEngine(application)
    val alertMgr          = SignalAlertManager(application)
    val portfolio         = PortfolioTracker(application)
    private val persist   = ModelPersistenceRepository(application)

    private val _uiState = MutableStateFlow(MLUiState())
    val uiState: StateFlow<MLUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            savedModels = persist.listSavedModels(),
            alertConfig = alertMgr.config
        )

        // Training progress
        viewModelScope.launch {
            engine.trainingProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(progress = progress, modelInfo = engine.modelInfo)
                if (progress.status == TrainingStatus.READY) {
                    _uiState.value = _uiState.value.copy(
                        featureImportance = engine.getFeatureImportance()
                    )
                    engine.modelInfo?.let { info ->
                        persist.saveModelInfo(info)
                        _uiState.value = _uiState.value.copy(savedModels = persist.listSavedModels())
                    }
                }
            }
        }

        // Real-time predictions → alerts + portfolio auto-check
        viewModelScope.launch {
            engine.latestPrediction.collect { pred ->
                if (pred != null) {
                    val history = (listOf(pred) + _uiState.value.predictionHistory).take(50)
                    _uiState.value = _uiState.value.copy(latestPrediction = pred, predictionHistory = history)
                    val sym = _uiState.value.symbol
                    alertMgr.evaluateEnsemble(pred, sym)
                    portfolio.checkPositions(sym, pred.price)
                }
            }
        }

        // Alerts
        viewModelScope.launch {
            alertMgr.alertHistory.collect { alerts ->
                _uiState.value = _uiState.value.copy(alertHistory = alerts)
            }
        }

        // Portfolio positions
        viewModelScope.launch {
            portfolio.positions.collect { positions ->
                _uiState.value = _uiState.value.copy(
                    openPositions  = positions.filter { it.isOpen },
                    closedPositions = positions.filter { !it.isOpen },
                    portfolioSummary = portfolio.getSummary()
                )
            }
        }
    }

    // ─── Training ─────────────────────────────────────────────────────────────
    fun setSymbol(symbol: String)   { _uiState.value = _uiState.value.copy(symbol = symbol) }
    fun setInterval(iv: CandleInterval) { _uiState.value = _uiState.value.copy(interval = iv) }

    fun startTraining() {
        val s = _uiState.value
        engine.train(symbol = s.symbol, interval = s.interval, ensembleConfig = s.ensembleConfig,
            scope = viewModelScope)
    }

    fun cancelTraining() {
        engine.cancelTraining()
        _uiState.value = _uiState.value.copy(progress = TrainingProgress(status = TrainingStatus.IDLE))
    }

    fun updateGbConfig(cfg: GBConfig) {
        _uiState.value = _uiState.value.copy(
            ensembleConfig = _uiState.value.ensembleConfig.copy(gbConfig = cfg), showGbConfig = false)
    }

    fun updateLstmConfig(cfg: LSTMConfig) {
        _uiState.value = _uiState.value.copy(
            ensembleConfig = _uiState.value.ensembleConfig.copy(lstmConfig = cfg), showLstmConfig = false)
    }

    fun updateEnsembleWeights(gbW: Double, lstmW: Double, disagreement: Double, minConf: Double) {
        _uiState.value = _uiState.value.copy(
            ensembleConfig = _uiState.value.ensembleConfig.copy(
                gbWeight = gbW, lstmWeight = lstmW,
                disagreementThreshold = disagreement, minConfidenceForSignal = minConf))
    }

    // ─── Alerts ───────────────────────────────────────────────────────────────
    fun updateAlertConfig(config: AlertConfig) {
        alertMgr.updateConfig(config)
        _uiState.value = _uiState.value.copy(alertConfig = config)
    }
    fun clearAlerts() = alertMgr.clearHistory()

    // ─── Portfolio ────────────────────────────────────────────────────────────
    fun openPositionFromLatestPrediction() {
        val pred = _uiState.value.latestPrediction ?: return
        portfolio.openFromEnsemble(pred, _uiState.value.symbol)
    }
    fun closePosition(positionId: String, exitPrice: Double) =
        portfolio.closePosition(positionId, exitPrice)
    fun deletePosition(positionId: String) = portfolio.deletePosition(positionId)
    fun clearPortfolio() = portfolio.clearAll()

    // ─── Saved models ─────────────────────────────────────────────────────────
    fun deleteModel(symbol: String, interval: String) {
        persist.deleteModel(symbol, interval)
        _uiState.value = _uiState.value.copy(savedModels = persist.listSavedModels())
    }

    // ─── UI ───────────────────────────────────────────────────────────────────
    fun setTab(tab: Int)        { _uiState.value = _uiState.value.copy(selectedTab = tab) }
    fun toggleGbConfig()        { _uiState.value = _uiState.value.copy(showGbConfig   = !_uiState.value.showGbConfig) }
    fun toggleLstmConfig()      { _uiState.value = _uiState.value.copy(showLstmConfig = !_uiState.value.showLstmConfig) }
    fun toggleBacktest()        { _uiState.value = _uiState.value.copy(showBacktest   = !_uiState.value.showBacktest) }

    // ── v2.0: individual model and cycle enhancer toggles ─────────────────────
    fun toggleUseGB() {
        val cfg = _uiState.value.ensembleConfig
        val newCfg = cfg.copy(useGradientBoosting = !cfg.useGradientBoosting)
        _uiState.value = _uiState.value.copy(ensembleConfig = newCfg)
        engine.updateConfig(newCfg)
    }
    fun toggleUseLSTM() {
        val cfg = _uiState.value.ensembleConfig
        val newCfg = cfg.copy(useLSTM = !cfg.useLSTM)
        _uiState.value = _uiState.value.copy(ensembleConfig = newCfg)
        engine.updateConfig(newCfg)
    }
    fun toggleMoonEnhancer() {
        val cfg = _uiState.value.ensembleConfig
        val newCfg = cfg.copy(useMoonCycleEnhancer = !cfg.useMoonCycleEnhancer)
        _uiState.value = _uiState.value.copy(ensembleConfig = newCfg)
        engine.updateConfig(newCfg)
    }
    fun toggleShemitahEnhancer() {
        val cfg = _uiState.value.ensembleConfig
        val newCfg = cfg.copy(useShemitahEnhancer = !cfg.useShemitahEnhancer)
        _uiState.value = _uiState.value.copy(ensembleConfig = newCfg)
        engine.updateConfig(newCfg)
    }

    val isTraining: Boolean get() = _uiState.value.progress.status in listOf(
        TrainingStatus.FETCHING_DATA, TrainingStatus.PREPROCESSING,
        TrainingStatus.TRAINING_GB, TrainingStatus.TRAINING_LSTM,
        TrainingStatus.COMBINING, TrainingStatus.BACKTESTING)
}
