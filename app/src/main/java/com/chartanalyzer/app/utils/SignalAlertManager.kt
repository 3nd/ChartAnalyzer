package com.chartanalyzer.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chartanalyzer.app.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * SignalAlertManager
 *
 * Fires Android push notifications when:
 *  - ML Ensemble produces STRONG_BUY or STRONG_SELL with high confidence
 *  - TA signal engine produces STRONG_BUY or STRONG_SELL with confluence
 *  - A trained model's prediction crosses a configurable threshold
 *
 * Alert rules:
 *  - Minimum confidence: configurable (default 0.70)
 *  - Minimum agreement (ensemble): configurable (default 0.75)
 *  - Cooldown: 1 alert per symbol per N minutes (default 15m)
 *  - Only fires for STRONG_BUY / STRONG_SELL by default
 *  - In-app alert history: last 50 alerts
 */
class SignalAlertManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID   = "chart_analyzer_signals"
        const val CHANNEL_NAME = "Trading Signals"
        private const val NOTIF_ID_BASE = 9000
    }

    private val notifIdCounter = AtomicInteger(NOTIF_ID_BASE)
    private val lastAlertTime  = mutableMapOf<String, Long>()  // symbol → timestamp
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _alertHistory = MutableStateFlow<List<SignalAlert>>(emptyList())
    val alertHistory: StateFlow<List<SignalAlert>> = _alertHistory.asStateFlow()

    var config = AlertConfig()

    init {
        createNotificationChannel()
    }

    // ─── Evaluate ML ensemble prediction ─────────────────────────────────────

    fun evaluateEnsemble(pred: EnsemblePrediction, symbol: String) {
        if (!config.enabled) return
        if (pred.signal == SignalType.NEUTRAL) return
        if (pred.signal == SignalType.BUY || pred.signal == SignalType.SELL) {
            if (!config.alertOnModerateSigs) return
        }
        if (pred.confidence < config.minConfidence) return
        if (pred.agreement < config.minAgreement) return
        if (isOnCooldown(symbol)) return

        val body = buildString {
            append("${pred.signal.name.replace('_',' ')} @ ${"%.4f".format(pred.price)}\n")
            append("Conf: ${"%.0f".format(pred.confidence * 100)}%  ")
            append("Agree: ${"%.0f".format(pred.agreement * 100)}%\n")
            append("GB: ${"%.0f".format(pred.gbPrediction.buyProbability * 100)}%buy  ")
            append("LSTM: ${"%.0f".format(pred.lstmPrediction.buyProbability * 100)}%buy")
        }

        fireAlert(
            symbol = symbol,
            signalType = pred.signal,
            title = "🤖 ML: ${pred.signal.name.replace('_',' ')} $symbol",
            body = body,
            source = AlertSource.ML_ENSEMBLE,
            confidence = pred.confidence,
            price = pred.price
        )
    }

    // ─── Evaluate TA prediction signal ────────────────────────────────────────

    fun evaluateTASignal(signal: PredictionSignal, symbol: String) {
        if (!config.enabled) return
        if (!config.alertOnTASignals) return
        if (signal.type == SignalType.NEUTRAL) return
        if (signal.type == SignalType.BUY || signal.type == SignalType.SELL) {
            if (!config.alertOnModerateSigs) return
        }
        if (signal.confidence < config.minConfidence) return
        if (isOnCooldown("TA_$symbol")) return

        val body = buildString {
            append("${signal.type.name.replace('_',' ')} @ ${"%.4f".format(signal.price)}\n")
            append("Conf: ${"%.0f".format(signal.confidence * 100)}%  ")
            append("Source: ${signal.source.name.replace('_',' ')}")
        }

        fireAlert(
            symbol = symbol,
            signalType = signal.type,
            title = "📊 TA: ${signal.type.name.replace('_',' ')} $symbol",
            body = body,
            source = AlertSource.TA_ENGINE,
            confidence = signal.confidence,
            price = signal.price
        )
    }

    // ─── Core fire alert ──────────────────────────────────────────────────────

    private fun fireAlert(
        symbol: String,
        signalType: SignalType,
        title: String,
        body: String,
        source: AlertSource,
        confidence: Double,
        price: Double
    ) {
        val cooldownKey = "${source.name}_$symbol"
        lastAlertTime[cooldownKey] = System.currentTimeMillis()

        val alert = SignalAlert(
            id = notifIdCounter.incrementAndGet(),
            symbol = symbol,
            signalType = signalType,
            title = title,
            body = body,
            source = source,
            confidence = confidence,
            price = price,
            timestamp = System.currentTimeMillis()
        )

        // Add to in-app history
        val newHistory = (listOf(alert) + _alertHistory.value).take(50)
        _alertHistory.value = newHistory

        // Fire push notification if enabled
        if (config.pushNotificationsEnabled) {
            sendNotification(alert)
        }
    }

    private fun sendNotification(alert: SignalAlert) {
        try {
            val (color, emoji) = when (alert.signalType) {
                SignalType.STRONG_BUY  -> Pair(0xFF00E676.toInt(), "🚀")
                SignalType.BUY         -> Pair(0xFF26A69A.toInt(), "📈")
                SignalType.SELL        -> Pair(0xFFFF6D00.toInt(), "📉")
                SignalType.STRONG_SELL -> Pair(0xFFFF1744.toInt(), "🔻")
                SignalType.NEUTRAL     -> Pair(0xFF4A5568.toInt(), "➖")
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$emoji ${alert.title}")
                .setContentText(alert.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                .setPriority(
                    if (alert.signalType == SignalType.STRONG_BUY ||
                        alert.signalType == SignalType.STRONG_SELL)
                        NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )
                .setColor(color)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context)
                .notify(alert.id, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted — silently skip
        } catch (e: Exception) {
            // Ignore notification errors
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time ML and TA buy/sell trading signals"
                enableLights(true)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun isOnCooldown(key: String): Boolean {
        val last = lastAlertTime[key] ?: return false
        return (System.currentTimeMillis() - last) < config.cooldownMinutes * 60_000L
    }

    fun clearHistory() {
        _alertHistory.value = emptyList()
    }

    fun updateConfig(newConfig: AlertConfig) {
        config = newConfig
    }
}

// ─── Alert data models ────────────────────────────────────────────────────────

enum class AlertSource { ML_ENSEMBLE, TA_ENGINE }

data class SignalAlert(
    val id: Int,
    val symbol: String,
    val signalType: SignalType,
    val title: String,
    val body: String,
    val source: AlertSource,
    val confidence: Double,
    val price: Double,
    val timestamp: Long
)

data class AlertConfig(
    val enabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val alertOnModerateSigs: Boolean = false,  // only STRONG by default
    val alertOnTASignals: Boolean = true,
    val minConfidence: Double = 0.70,
    val minAgreement: Double = 0.75,
    val cooldownMinutes: Int = 15
)
