package com.chartanalyzer.app.utils

import android.content.Context
import com.chartanalyzer.app.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID

// ─── Position models ──────────────────────────────────────────────────────────

enum class PositionSide { LONG, SHORT }
enum class PositionStatus { OPEN, CLOSED, STOPPED_OUT, TARGET_HIT }

data class Position(
    val id: String = UUID.randomUUID().toString(),
    val symbol: String,
    val side: PositionSide,
    val entryPrice: Double,
    val quantity: Double = 1.0,
    val entryTime: Long = System.currentTimeMillis(),
    val entrySignal: SignalType,
    val entrySource: String,          // "ML_ENSEMBLE" or "TA_ENGINE"
    val entryConfidence: Double,
    val targetPrice: Double? = null,
    val stopLoss: Double? = null,
    val exitPrice: Double? = null,
    val exitTime: Long? = null,
    val status: PositionStatus = PositionStatus.OPEN,
    val notes: String = ""
) {
    val isOpen: Boolean get() = status == PositionStatus.OPEN
    val pnlPct: Double?
        get() {
            val exit = exitPrice ?: return null
            return if (side == PositionSide.LONG)
                (exit - entryPrice) / entryPrice * 100.0
            else
                (entryPrice - exit) / entryPrice * 100.0
        }

    val riskReward: Double?
        get() {
            val t = targetPrice ?: return null
            val s = stopLoss ?: return null
            val reward = Math.abs(t - entryPrice)
            val risk   = Math.abs(s - entryPrice)
            return if (risk > 0) reward / risk else null
        }

    val currentPnlPct: Double
        get() = pnlPct ?: 0.0
}

data class PortfolioSummary(
    val totalPositions: Int,
    val openPositions: Int,
    val closedPositions: Int,
    val winningPositions: Int,
    val losingPositions: Int,
    val totalPnlPct: Double,
    val winRate: Double,
    val avgWinPct: Double,
    val avgLossPct: Double,
    val bestTrade: Position?,
    val worstTrade: Position?,
    val profitFactor: Double
)

/**
 * PortfolioTracker
 *
 * Tracks paper trading positions opened from ML/TA signals.
 * All trades are paper (simulated) by default — no real money.
 * Persists to disk so positions survive app restarts.
 */
class PortfolioTracker(private val context: Context) {

    private val gson = Gson()
    private val positionsFile = File(context.filesDir, "portfolio_positions.json")

    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    val openPositions: List<Position> get() = _positions.value.filter { it.isOpen }
    val closedPositions: List<Position> get() = _positions.value.filter { !it.isOpen }

    init {
        loadFromDisk()
    }

    // ─── Open position from prediction ────────────────────────────────────────

    fun openFromEnsemble(
        pred: EnsemblePrediction,
        symbol: String,
        quantity: Double = 1.0
    ): Position? {
        if (pred.signal == SignalType.NEUTRAL) return null
        val isBuy = pred.signal == SignalType.BUY || pred.signal == SignalType.STRONG_BUY
        val side = if (isBuy) PositionSide.LONG else PositionSide.SHORT
        val atrProxy = pred.price * 0.015  // 1.5% as rough ATR proxy
        val target = if (isBuy) pred.price + atrProxy * 2.0 else pred.price - atrProxy * 2.0
        val stop   = if (isBuy) pred.price - atrProxy * 1.0 else pred.price + atrProxy * 1.0

        val pos = Position(
            symbol = symbol,
            side = side,
            entryPrice = pred.price,
            quantity = quantity,
            entrySignal = pred.signal,
            entrySource = "ML_ENSEMBLE",
            entryConfidence = pred.confidence,
            targetPrice = target,
            stopLoss = stop
        )
        addPosition(pos)
        return pos
    }

    fun openFromTASignal(
        signal: PredictionSignal,
        symbol: String,
        quantity: Double = 1.0
    ): Position? {
        if (signal.type == SignalType.NEUTRAL) return null
        val isBuy = signal.type == SignalType.BUY || signal.type == SignalType.STRONG_BUY
        val side = if (isBuy) PositionSide.LONG else PositionSide.SHORT

        val pos = Position(
            symbol = symbol,
            side = side,
            entryPrice = signal.price,
            quantity = quantity,
            entrySignal = signal.type,
            entrySource = signal.source.name,
            entryConfidence = signal.confidence,
            targetPrice = signal.targetPrice,
            stopLoss = signal.stopLoss
        )
        addPosition(pos)
        return pos
    }

    // ─── Close position ────────────────────────────────────────────────────────

    fun closePosition(positionId: String, exitPrice: Double, reason: PositionStatus = PositionStatus.CLOSED) {
        val current = _positions.value.toMutableList()
        val idx = current.indexOfFirst { it.id == positionId }
        if (idx < 0) return
        current[idx] = current[idx].copy(
            exitPrice = exitPrice,
            exitTime = System.currentTimeMillis(),
            status = reason
        )
        _positions.value = current
        saveToDisk()
    }

    // ─── Auto-check positions against current price ──────────────────────────

    fun checkPositions(symbol: String, currentPrice: Double) {
        val toClose = mutableListOf<Pair<String, Pair<Double, PositionStatus>>>()
        _positions.value.filter { it.symbol == symbol && it.isOpen }.forEach { pos ->
            pos.targetPrice?.let { target ->
                val hitTarget = (pos.side == PositionSide.LONG && currentPrice >= target) ||
                                (pos.side == PositionSide.SHORT && currentPrice <= target)
                if (hitTarget) toClose.add(pos.id to Pair(currentPrice, PositionStatus.TARGET_HIT))
            }
            pos.stopLoss?.let { stop ->
                val hitStop = (pos.side == PositionSide.LONG && currentPrice <= stop) ||
                              (pos.side == PositionSide.SHORT && currentPrice >= stop)
                if (hitStop) toClose.add(pos.id to Pair(currentPrice, PositionStatus.STOPPED_OUT))
            }
        }
        toClose.forEach { (id, pair) ->
            closePosition(id, pair.first, pair.second)
        }
    }

    // ─── Portfolio summary ────────────────────────────────────────────────────

    fun getSummary(): PortfolioSummary {
        val all = _positions.value
        val closed = all.filter { !it.isOpen && it.pnlPct != null }
        val winning = closed.filter { (it.pnlPct ?: 0.0) > 0 }
        val losing  = closed.filter { (it.pnlPct ?: 0.0) <= 0 }
        val totalPnl = closed.sumOf { it.pnlPct ?: 0.0 }
        val winRate  = if (closed.isNotEmpty()) winning.size.toDouble() / closed.size else 0.0
        val avgWin   = if (winning.isNotEmpty()) winning.sumOf { it.pnlPct ?: 0.0 } / winning.size else 0.0
        val avgLoss  = if (losing.isNotEmpty())  losing.sumOf  { Math.abs(it.pnlPct ?: 0.0) } / losing.size else 0.0
        val totalWin = winning.sumOf { it.pnlPct ?: 0.0 }
        val totalLoss = losing.sumOf { Math.abs(it.pnlPct ?: 0.0) }
        val pf = if (totalLoss > 0) totalWin / totalLoss else if (totalWin > 0) Double.MAX_VALUE else 0.0

        return PortfolioSummary(
            totalPositions  = all.size,
            openPositions   = all.count { it.isOpen },
            closedPositions = closed.size,
            winningPositions = winning.size,
            losingPositions  = losing.size,
            totalPnlPct = totalPnl,
            winRate = winRate,
            avgWinPct  = avgWin,
            avgLossPct = avgLoss,
            bestTrade  = closed.maxByOrNull { it.pnlPct ?: Double.MIN_VALUE },
            worstTrade = closed.minByOrNull { it.pnlPct ?: Double.MAX_VALUE },
            profitFactor = pf.coerceAtMost(99.0)
        )
    }

    private fun addPosition(pos: Position) {
        _positions.value = listOf(pos) + _positions.value
        saveToDisk()
    }

    fun deletePosition(id: String) {
        _positions.value = _positions.value.filter { it.id != id }
        saveToDisk()
    }

    fun clearAll() {
        _positions.value = emptyList()
        saveToDisk()
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun saveToDisk() {
        try {
            val type = object : TypeToken<List<Position>>() {}.type
            FileWriter(positionsFile).use { it.write(gson.toJson(_positions.value, type)) }
        } catch (_: Exception) {}
    }

    private fun loadFromDisk() {
        try {
            if (!positionsFile.exists()) return
            val type = object : TypeToken<List<Position>>() {}.type
            val loaded: List<Position> = gson.fromJson(FileReader(positionsFile), type) ?: emptyList()
            _positions.value = loaded
        } catch (_: Exception) {}
    }
}
